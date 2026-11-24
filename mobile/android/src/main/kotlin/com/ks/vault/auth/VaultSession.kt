package com.ks.vault.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.ks.vault.crypto.FileCipher
import com.ks.vault.crypto.KeystoreWrapper
import com.ks.vault.crypto.SecretKind
import com.ks.vault.crypto.VaultKeys
import com.ks.vault.crypto.VaultUnreadableException
import com.ks.vault.crypto.wipe
import com.ks.vault.data.VaultDatabase
import com.ks.vault.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whether the vault is open, for the whole app. Unlocking opens the encrypted database; locking
 * closes it and drops the repository, so no decrypted data stays reachable. The process dying
 * locks it too.
 */
class VaultSession(private val context: Context, private val settings: VaultSettings) {

    sealed interface State {
        data object NeedsSetup : State
        data class Locked(val kind: SecretKind) : State
        data class Unlocked(val vault: VaultRepository, val kind: SecretKind) : State
    }

    sealed interface UnlockResult {
        data object Ok : UnlockResult
        /** Wrong secret; [waitMillis] until the next try is allowed (0: now). */
        data class Wrong(val waitMillis: Long, val failures: Int) : UnlockResult
        data class Wait(val waitMillis: Long) : UnlockResult
        data object Unreadable : UnlockResult
    }

    private val keys = VaultKeys(File(context.noBackupFilesDir, "vault.key"), KeystoreWrapper())
    private val files = FileCipher(File(context.noBackupFilesDir, "attachments"))
    val lockout = Lockout(PrefsLockoutStore(context), System::currentTimeMillis)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var idleJob: Job? = null
    @Volatile private var lastTouch = SystemClock.elapsedRealtime()

    /** Set while a picker the app opened is in front, so leaving the app for it does not lock. */
    @Volatile var externalUntil = 0L
        private set

    private fun initialState(): State {
        val kind = keys.kind()
        return if (kind == null) State.NeedsSetup else State.Locked(kind)
    }

    /** Sets up a new vault and opens it. [secret] is wiped. */
    suspend fun setUp(secret: CharArray, kind: SecretKind) {
        val vaultKey = withContext(Dispatchers.Default) {
            try { keys.create(secret, kind) } finally { secret.wipe() }
        }
        open(vaultKey, kind)
        lockout.recordSuccess()
    }

    /** Tries [secret], which is wiped. */
    suspend fun unlock(secret: CharArray): UnlockResult {
        try {
            val wait = lockout.waitMillis()
            if (wait > 0) return UnlockResult.Wait(wait)
            val kind = keys.kind() ?: return UnlockResult.Unreadable
            val vaultKey = try {
                withContext(Dispatchers.Default) { keys.unlock(secret) }
            } catch (_: VaultUnreadableException) {
                return UnlockResult.Unreadable
            }
            if (vaultKey == null) {
                lockout.recordFailure()
                return UnlockResult.Wrong(lockout.waitMillis(), lockout.failures)
            }
            lockout.recordSuccess()
            open(vaultKey, kind)
            return UnlockResult.Ok
        } finally {
            secret.wipe()
        }
    }

    /** Checks [current] and replaces it with [newSecret]. Both are wiped. False when [current] is wrong. */
    suspend fun changeSecret(current: CharArray, newSecret: CharArray, newKind: SecretKind): Boolean {
        try {
            val ok = withContext(Dispatchers.Default) { keys.change(current, newSecret, newKind) }
            val s = _state.value
            if (ok && s is State.Unlocked) _state.value = s.copy(kind = newKind)
            return ok
        } finally {
            current.wipe()
            newSecret.wipe()
        }
    }

    /** Checks [secret] without changing the lock state (for confirming a change). */
    suspend fun check(secret: CharArray): Boolean = try {
        withContext(Dispatchers.Default) { keys.unlock(secret)?.also { it.wipe() } != null }
    } catch (_: VaultUnreadableException) {
        false
    } finally {
        secret.wipe()
    }

    private suspend fun open(vaultKey: ByteArray, kind: SecretKind) {
        val repo = try {
            withContext(Dispatchers.IO) {
                VaultRepository(VaultDatabase.open(context, vaultKey), files).also { it.removeStrayFiles() }
            }
        } finally {
            vaultKey.wipe()
        }
        lastTouch = SystemClock.elapsedRealtime()
        _state.value = State.Unlocked(repo, kind)
        startIdleTimer()
    }

    /** Locks now: closes the database and forgets the repository. */
    fun lock() {
        idleJob?.cancel()
        externalUntil = 0
        val s = _state.value
        if (s is State.Unlocked) {
            _state.value = State.Locked(s.kind)
            // The screens drop the repository with this state change; the database is closed just
            // after, so a query still running on the way out does not hit a closed database.
            scope.launch {
                delay(CLOSE_DELAY_MS)
                s.vault.close()
            }
        }
    }

    /** Erases the whole vault (forgotten secret): key file, Keystore key, database and attachments. */
    suspend fun eraseEverything() {
        val open = (_state.value as? State.Unlocked)?.vault
        lock()
        withContext(Dispatchers.IO) {
            open?.close()
            keys.erase()
            context.deleteDatabase(VaultDatabase.NAME)
            File(context.noBackupFilesDir, "attachments").deleteRecursively()
        }
        lockout.recordSuccess()
        _state.value = State.NeedsSetup
    }

    /** Any touch or key press: the idle timer starts again. */
    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    /** Called before opening a picker: leaving the app for up to [EXTERNAL_GRACE_MS] does not lock. */
    fun expectExternal() {
        externalUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS
    }

    fun externalDone() {
        externalUntil = 0
        touch()
    }

    /** The app went to the background. */
    fun onBackground() {
        if (SystemClock.elapsedRealtime() < externalUntil) return
        lock()
    }

    /** The app came back: lock if a picker grace ran out while away. */
    fun onForeground() {
        if (externalUntil != 0L && SystemClock.elapsedRealtime() >= externalUntil) lock()
    }

    private fun startIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(5_000)
                val idle = SystemClock.elapsedRealtime() - lastTouch
                if (externalUntil == 0L && idle >= settings.autoLockMinutes * 60_000L) lock()
            }
        }
    }

    companion object {
        const val EXTERNAL_GRACE_MS = 3 * 60_000L
        private const val CLOSE_DELAY_MS = 300L
    }
}

/** commit(), not apply(): a failed try must be on disk before the next one, even if the app is killed right after. */
@SuppressLint("ApplySharedPref")
private class PrefsLockoutStore(context: Context) : LockoutStore {
    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)
    override var failures: Int
        get() = prefs.getInt("failures", 0)
        set(v) { prefs.edit().putInt("failures", v).commit() }
    override var lastFailureAt: Long
        get() = prefs.getLong("lastFailureAt", 0)
        set(v) { prefs.edit().putLong("lastFailureAt", v).commit() }
}

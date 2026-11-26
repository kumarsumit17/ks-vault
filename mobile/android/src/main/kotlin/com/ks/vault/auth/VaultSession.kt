package com.ks.vault.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.ks.vault.crypto.BiometricKey
import com.ks.vault.crypto.FileCipher
import javax.crypto.Cipher
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
    private val bioFile = File(context.noBackupFilesDir, "vault.bio")
    private val bioKey = BiometricKey()
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    /** The vault key held only between checking the secret and the biometric prompt that seals it. */
    private var pendingBioKey: ByteArray? = null

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
        rememberBootCount()
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
            rememberBootCount()
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
            disableBiometric()
            keys.erase()
            context.deleteDatabase(VaultDatabase.NAME)
            File(context.noBackupFilesDir, "attachments").deleteRecursively()
        }
        lockout.recordSuccess()
        _state.value = State.NeedsSetup
    }

    // ---- Fingerprint or face unlock --------------------------------------------------------------

    val biometricEnabled: Boolean get() = bioFile.isFile

    private fun bootCount(): Int? =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()

    private fun rememberBootCount() {
        bootCount()?.let { prefs.edit().putInt(KEY_BOOT, it).apply() }
    }

    /** True when the unlock screen may offer the biometric prompt now ([BiometricGate]). */
    fun biometricAllowedNow(): Boolean = BiometricGate.allowed(
        enabled = biometricEnabled,
        bootCountNow = bootCount(),
        bootCountAtSecretUnlock = if (prefs.contains(KEY_BOOT)) prefs.getInt(KEY_BOOT, -1) else null,
        lockoutWaitMillis = lockout.waitMillis(),
    )

    /**
     * First half of turning biometrics on: checks [secret] (wiped) and returns the cipher to hand to the
     * biometric prompt, or null when the secret is wrong.
     */
    suspend fun prepareEnableBiometric(secret: CharArray): Cipher? {
        val vaultKey = try {
            withContext(Dispatchers.Default) { keys.unlock(secret) }
        } catch (_: VaultUnreadableException) {
            null
        } finally {
            secret.wipe()
        } ?: return null
        pendingBioKey?.wipe()
        pendingBioKey = vaultKey
        bioKey.destroy() // a fresh key each time it is turned on
        return bioKey.encryptCipher()
    }

    /** Second half: the prompt succeeded, so [cipher] can seal the vault key. */
    fun finishEnableBiometric(cipher: Cipher) {
        val vaultKey = pendingBioKey ?: return
        try {
            val box = cipher.iv + cipher.doFinal(vaultKey)
            val tmp = File(bioFile.path + ".new")
            tmp.writeBytes(box)
            tmp.renameTo(bioFile)
        } finally {
            vaultKey.wipe()
            pendingBioKey = null
        }
    }

    fun cancelEnableBiometric() {
        pendingBioKey?.wipe()
        pendingBioKey = null
    }

    fun disableBiometric() {
        cancelEnableBiometric()
        bioFile.delete()
        runCatching { bioKey.destroy() }
    }

    /**
     * The cipher for the unlock prompt, or null when biometrics cannot be used: then they are turned off
     * (for example a new fingerprint was enrolled, which destroys the key).
     */
    fun biometricUnlockCipher(): Cipher? = try {
        bioKey.decryptCipher(bioFile.readBytes().copyOf(BiometricKey.NONCE_BYTES))
    } catch (_: Exception) {
        disableBiometric()
        null
    }

    /** The prompt succeeded: [cipher] opens the vault key and the vault opens. */
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean {
        val kind = keys.kind() ?: return false
        val vaultKey = try {
            val box = bioFile.readBytes()
            cipher.doFinal(box, BiometricKey.NONCE_BYTES, box.size - BiometricKey.NONCE_BYTES)
        } catch (_: Exception) {
            disableBiometric()
            return false
        }
        open(vaultKey, kind)
        return true
    }

    // ---- Files shared into the vault ------------------------------------------------------------

    private val _shared = MutableStateFlow<List<android.net.Uri>>(emptyList())
    /** Photos and PDFs another app shared with "Save to KS Vault", waiting for the vault to be unlocked. */
    val shared: StateFlow<List<android.net.Uri>> = _shared.asStateFlow()

    fun receiveShared(uris: List<android.net.Uri>) {
        if (uris.isNotEmpty()) _shared.value = uris
    }

    fun clearShared() {
        _shared.value = emptyList()
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
        private const val KEY_BOOT = "bootCountAtSecretUnlock"
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

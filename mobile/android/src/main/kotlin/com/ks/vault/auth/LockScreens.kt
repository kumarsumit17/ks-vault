package com.ks.vault.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.crypto.SecretKind
import com.ks.vault.ui.PatternPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entry of one secret: a password field with a button, or the pattern pad. [onSubmit] gets the
 * secret's characters (the caller wipes them). The typed text is cleared after each submit.
 */
@Composable
fun SecretEntry(
    kind: SecretKind,
    buttonText: String,
    enabled: Boolean,
    onSubmit: (CharArray) -> Unit,
    onPattern: ((List<Int>) -> Unit)? = null,
) {
    when (kind) {
        SecretKind.PASSWORD -> {
            var text by remember { mutableStateOf("") }
            val submit = {
                if (text.isNotEmpty()) {
                    val chars = text.toCharArray()
                    text = ""
                    onSubmit(chars)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.lock_password)) },
                singleLine = true,
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("password"),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = submit, enabled = enabled && text.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("submit")) {
                Text(buttonText)
            }
        }
        SecretKind.PATTERN -> PatternPad(
            description = stringResource(R.string.lock_pattern_pad),
            enabled = enabled,
            onDone = { dots ->
                if (onPattern != null) onPattern(dots) else onSubmit(Secrets.patternChars(dots))
            },
        )
    }
}

@Composable
private fun LockLayout(title: String, message: String?, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("lockMessage"))
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
    }
}

/**
 * Choosing a new secret: its kind, then entering it twice. Used for the first setup and for
 * changing the secret. [onChosen] gets the secret (the caller wipes it).
 */
@Composable
fun ChooseSecret(title: String, intro: String?, busy: Boolean, onChosen: (CharArray, SecretKind) -> Unit, extra: @Composable () -> Unit = {}) {
    var kind by remember { mutableStateOf(SecretKind.PASSWORD) }
    var first by remember { mutableStateOf<CharArray?>(null) }
    var message by remember { mutableStateOf<String?>(intro) }
    val tooShortPassword = stringResource(R.string.lock_password_too_short, Secrets.MIN_PASSWORD.toString())
    val tooShortPattern = stringResource(R.string.lock_pattern_too_short, Secrets.MIN_PATTERN_DOTS.toString())
    val again = stringResource(R.string.lock_enter_again)
    val mismatch = stringResource(R.string.lock_mismatch)

    fun accept(secret: CharArray) {
        val f = first
        if (f == null) {
            first = secret
            message = again
        } else {
            val same = f.contentEquals(secret)
            f.fill('\u0000')
            first = null
            if (same) {
                onChosen(secret, kind)
            } else {
                secret.fill('\u0000')
                message = mismatch
            }
        }
    }

    LockLayout(title, if (busy) stringResource(R.string.lock_working) else message) {
        if (busy) {
            CircularProgressIndicator()
        } else {
        // The kind stays on screen (fixed while confirming), so the entry does not move between the two tries.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kind == SecretKind.PASSWORD,
                onClick = { kind = SecretKind.PASSWORD; message = intro },
                enabled = first == null,
                label = { Text(stringResource(R.string.lock_kind_password)) },
            )
            FilterChip(
                selected = kind == SecretKind.PATTERN,
                onClick = { kind = SecretKind.PATTERN; message = intro },
                enabled = first == null,
                label = { Text(stringResource(R.string.lock_kind_pattern)) },
                modifier = Modifier.testTag("kindPattern"),
            )
        }
        Spacer(Modifier.height(16.dp))
        SecretEntry(
            kind = kind,
            buttonText = stringResource(if (first == null) R.string.common_next else R.string.common_done),
            enabled = true,
            onSubmit = { secret ->
                if (first == null && !Secrets.passwordLongEnough(secret)) {
                    secret.fill('\u0000')
                    message = tooShortPassword
                } else {
                    accept(secret)
                }
            },
            onPattern = { dots ->
                if (first == null && !Secrets.patternLongEnough(dots)) message = tooShortPattern
                else accept(Secrets.patternChars(dots))
            },
        )
        extra()
        }
    }
}

/** First start: the warning that there is no recovery, then choosing the secret. */
@Composable
fun SetupScreen(session: VaultSession) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    ChooseSecret(
        title = stringResource(R.string.setup_title),
        intro = stringResource(R.string.setup_intro),
        busy = busy,
        onChosen = { secret, kind ->
            busy = true
            scope.launch { session.setUp(secret, kind) }
        },
    )
}

@Composable
fun UnlockScreen(session: VaultSession, kind: SecretKind) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var waitUntil by remember { mutableLongStateOf(System.currentTimeMillis() + session.lockout.waitMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wrongTries by remember { mutableStateOf(0) }
    var unreadable by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }

    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    val waiting = now < waitUntil
    val message = when {
        unreadable -> stringResource(R.string.unlock_unreadable)
        waiting -> stringResource(R.string.unlock_wait, formatWait(waitUntil - now))
        wrongTries > 0 -> {
            val left = (Lockout.FREE_TRIES - session.lockout.failures).coerceAtLeast(0)
            if (left > 0) pluralStringResource(R.plurals.unlock_wrong, left, left.toString())
            else stringResource(R.string.unlock_wrong_last)
        }
        else -> null
    }

    val context = LocalContext.current
    val bioTitle = stringResource(R.string.bio_prompt_title)
    val bioNegative = stringResource(if (kind == SecretKind.PATTERN) R.string.bio_use_pattern else R.string.bio_use_password)
    var bioOffered by remember { mutableStateOf(session.biometricAllowedNow()) }
    fun promptBiometric() {
        val activity = context.findFragmentActivity() ?: return
        val cipher = session.biometricUnlockCipher()
        if (cipher == null) {
            bioOffered = false
            return
        }
        showBiometricPrompt(activity, bioTitle, bioNegative, cipher, onSuccess = { c ->
            busy = true
            scope.launch {
                if (!session.unlockWithBiometric(c)) bioOffered = false
                busy = false
            }
        }, onEnd = {})
    }
    // Offer the fingerprint or face once, as soon as the lock screen shows.
    LaunchedEffect(Unit) { if (bioOffered) promptBiometric() }

    LockLayout(stringResource(R.string.unlock_title), message) {
        if (busy) {
            CircularProgressIndicator()
        } else if (!unreadable) {
            SecretEntry(
                kind = kind,
                buttonText = stringResource(R.string.unlock_button),
                enabled = !waiting,
                onSubmit = { secret ->
                    busy = true
                    scope.launch {
                        when (val r = session.unlock(secret)) {
                            VaultSession.UnlockResult.Ok -> Unit
                            is VaultSession.UnlockResult.Wrong -> {
                                wrongTries++
                                waitUntil = System.currentTimeMillis() + r.waitMillis
                                now = System.currentTimeMillis()
                            }
                            is VaultSession.UnlockResult.Wait -> {
                                waitUntil = System.currentTimeMillis() + r.waitMillis
                                now = System.currentTimeMillis()
                            }
                            VaultSession.UnlockResult.Unreadable -> unreadable = true
                        }
                        busy = false
                    }
                },
            )
        }
        if (bioOffered && !busy && !unreadable) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { promptBiometric() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bio_unlock_button))
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { confirmErase = true }) {
            Text(stringResource(R.string.unlock_forgot))
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    scope.launch { session.eraseEverything() }
                }) { Text(stringResource(R.string.erase_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** "4:59" style minutes and seconds (Latin digits). */
fun formatWait(millis: Long): String {
    val s = (millis + 999) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
}

package com.ks.vault.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.SecretEntry
import com.ks.vault.auth.VaultSession
import com.ks.vault.auth.findFragmentActivity
import com.ks.vault.auth.showBiometricPrompt
import com.ks.vault.auth.strongBiometricReady
import com.ks.vault.crypto.SecretKind
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route
import kotlinx.coroutines.launch

/** The Settings row: a switch, or a hint when the phone has no strong fingerprint or face set up. */
@Composable
fun BiometricSetting(session: VaultSession, nav: Navigator) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(session.biometricEnabled) }
    val ready = remember { strongBiometricReady(context) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.bio_setting), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (ready || enabled) R.string.bio_setting_hint else R.string.bio_not_ready),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = enabled,
            enabled = ready || enabled,
            onCheckedChange = { on ->
                if (on) {
                    nav.go(Route.EnableBiometric)
                } else {
                    session.disableBiometric()
                    enabled = false
                }
            },
            modifier = Modifier.testTag("bioSwitch"),
        )
    }
}

/** Turning biometrics on: the current secret, then the fingerprint or face once. */
@Composable
fun EnableBiometricScreen(session: VaultSession, nav: Navigator, kind: SecretKind) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    val title = stringResource(R.string.bio_enable_prompt)
    val cancel = stringResource(R.string.common_cancel)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text(stringResource(R.string.bio_enable_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.bio_enable_intro), style = MaterialTheme.typography.bodyMedium)
            if (wrong) Text(stringResource(R.string.change_wrong), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            SecretEntry(
                kind = kind,
                buttonText = stringResource(R.string.common_next),
                enabled = !busy,
                onSubmit = { secret ->
                    busy = true
                    scope.launch {
                        val cipher = session.prepareEnableBiometric(secret)
                        val activity = context.findFragmentActivity()
                        if (cipher == null || activity == null) {
                            wrong = cipher == null
                            busy = false
                            return@launch
                        }
                        showBiometricPrompt(activity, title, cancel, cipher, onSuccess = { c ->
                            session.finishEnableBiometric(c)
                            nav.back()
                        }, onEnd = {
                            session.cancelEnableBiometric()
                            busy = false
                        })
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { session.cancelEnableBiometric(); nav.back() }) { Text(cancel) }
        }
    }
}

package com.ks.vault.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.ChooseSecret
import com.ks.vault.auth.SecretEntry
import com.ks.vault.auth.VaultSession
import com.ks.vault.crypto.SecretKind
import com.ks.vault.ui.Navigator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlinx.coroutines.launch

/** Enter the current secret, then choose the new one (it may be of the other kind). */
@Composable
fun ChangeSecretScreen(session: VaultSession, nav: Navigator, kind: SecretKind) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { current?.fill('\u0000') } }
    BackHandler { nav.back() }

    val c = current
    if (c == null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                Text(stringResource(R.string.change_current), style = MaterialTheme.typography.headlineSmall)
                if (wrong) Text(stringResource(R.string.change_wrong), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                SecretEntry(
                    kind = kind,
                    buttonText = stringResource(R.string.common_next),
                    enabled = !busy,
                    onSubmit = { secret ->
                        busy = true
                        val copy = secret.copyOf()
                        scope.launch {
                            if (session.check(secret)) current = copy else { copy.fill('\u0000'); wrong = true }
                            busy = false
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { nav.back() }) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    } else {
        ChooseSecret(
            title = stringResource(R.string.change_new),
            intro = null,
            busy = busy,
            onChosen = { secret, newKind ->
                busy = true
                scope.launch {
                    session.changeSecret(c, secret, newKind)
                    current = null
                    nav.back()
                }
            },
            extra = {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { nav.back() }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

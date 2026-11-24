package com.ks.vault.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ks.utility.appupdate.AppUpdate
import com.ks.utility.appupdate.GitHubRepo
import com.ks.vault.BuildConfig
import com.ks.vault.R
import com.ks.vault.auth.VaultSession
import com.ks.vault.auth.VaultSettings
import com.ks.vault.crypto.SecretKind
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route

private val REPO = GitHubRepo.parse("kumarsumit17/ks-vault")!!

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(session: VaultSession, settings: VaultSettings, nav: Navigator) {
    val context = LocalContext.current
    val autoLock by settings.autoLock.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()
    val kind = (state as? VaultSession.State.Unlocked)?.kind ?: SecretKind.PASSWORD
    var language by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section(stringResource(R.string.settings_lock))
            Text(
                stringResource(if (kind == SecretKind.PATTERN) R.string.settings_lock_pattern else R.string.settings_lock_password),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { nav.go(Route.ChangeSecret) }) { Text(stringResource(R.string.settings_change_secret)) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.settings_auto_lock_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultSettings.AUTO_LOCK_CHOICES.forEach { m ->
                    FilterChip(
                        selected = autoLock == m,
                        onClick = { settings.setAutoLockMinutes(m) },
                        label = { Text(pluralStringResource(R.plurals.settings_minutes, m, m.toString())) },
                    )
                }
            }

            Divider()
            Section(stringResource(R.string.common_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "" to R.string.common_language_system,
                    "en" to R.string.common_language_english,
                    "hi" to R.string.common_language_hindi,
                ).forEach { (tag, label) ->
                    FilterChip(
                        selected = language == tag,
                        onClick = {
                            language = tag
                            // Android keeps the choice; "Same as the system" is an empty list.
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            Divider()
            Section(stringResource(R.string.common_app_update_section))
            val version = BuildConfig.VERSION_NAME
            Text(
                if (version == AppUpdate.DEV_VERSION) stringResource(R.string.common_app_update_dev)
                else stringResource(R.string.common_app_update_current, version),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.settings_update_offline), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                // The browser opens the page; KS Vault itself sends nothing.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO.latestReleasePageUrl)))
                } catch (_: ActivityNotFoundException) {
                }
            }) { Text(stringResource(R.string.common_app_update_get_button)) }

            Divider()
            Section(stringResource(R.string.settings_about))
            Text(stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

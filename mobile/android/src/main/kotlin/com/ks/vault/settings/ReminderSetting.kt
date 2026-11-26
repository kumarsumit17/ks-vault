package com.ks.vault.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ks.vault.R
import com.ks.vault.auth.VaultSettings

/** The expiry reminder switch; turning it on asks for the notification permission (Android 13+). */
@Composable
fun ReminderSetting(settings: VaultSettings) {
    val context = LocalContext.current
    val on by settings.reminders.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settings.setReminders(true)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.reminder_setting), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.reminder_setting_hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = on,
            onCheckedChange = { want ->
                if (!want) {
                    settings.setReminders(false)
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settings.setReminders(true)
                }
            },
            modifier = Modifier.testTag("reminderSwitch"),
        )
    }
}

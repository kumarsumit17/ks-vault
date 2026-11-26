package com.ks.vault.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.VaultSession
import com.ks.vault.backup.BackupFormat
import com.ks.vault.backup.WrongBackupPasswordException
import com.ks.vault.crypto.DamagedStreamException
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupLayout(title: String, nav: Navigator, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Back up the whole vault to a file the user chooses, sealed with a backup password typed twice. */
@Composable
fun BackupScreen(vault: VaultRepository, session: VaultSession, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val tooShort = stringResource(R.string.backup_password_too_short, BackupFormat.MIN_PASSWORD.toString())
    val mismatch = stringResource(R.string.backup_password_mismatch)
    val failed = stringResource(R.string.backup_failed)
    val res = context.resources

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        session.externalDone()
        val password = pending
        pending = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            message = try {
                val count = context.contentResolver.openOutputStream(uri, "wt")!!.use { vault.backup(it, password) }
                res.getQuantityString(R.plurals.backup_done, count, count.toString())
            } catch (_: Exception) {
                failed
            } finally {
                password.fill('\u0000')
            }
            busy = false
        }
    }

    BackupLayout(stringResource(R.string.backup_title), nav) {
        Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
        if (busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.backup_working))
            return@BackupLayout
        }
        PasswordField(stringResource(R.string.backup_password), first, { first = it; message = null }, "backupPassword")
        PasswordField(stringResource(R.string.backup_password_again), second, { second = it; message = null }, "backupPasswordAgain")
        Button(
            onClick = {
                when {
                    first.length < BackupFormat.MIN_PASSWORD -> message = tooShort
                    first != second -> message = mismatch
                    else -> {
                        pending = first.toCharArray()
                        first = ""
                        second = ""
                        session.expectExternal()
                        create.launch("KS-Vault-${LocalDate.now()}.ksvault")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("backupChoose"),
        ) { Text(stringResource(R.string.backup_choose)) }
        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("backupMessage")) }
    }
}

/** Restore a backup file: choose it, type its password, then add to the vault or replace it. */
@Composable
fun RestoreScreen(vault: VaultRepository, session: VaultSession, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.restore_wrong_password)
    val damaged = stringResource(R.string.restore_damaged)
    val failed = stringResource(R.string.restore_failed)
    val res = context.resources

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        session.externalDone()
        if (uri != null) {
            file = uri
            message = null
        }
    }

    fun restore(replace: Boolean) {
        val uri = file ?: return
        val secret = password.toCharArray()
        password = ""
        busy = true
        scope.launch {
            message = try {
                val count = context.contentResolver.openInputStream(uri)!!.use { vault.restore(it, secret, replace) }
                res.getQuantityString(R.plurals.restore_done, count, count.toString())
            } catch (_: WrongBackupPasswordException) {
                wrong
            } catch (_: DamagedStreamException) {
                damaged
            } catch (_: Exception) {
                failed
            } finally {
                secret.fill('\u0000')
            }
            busy = false
        }
    }

    BackupLayout(stringResource(R.string.restore_title), nav) {
        Text(stringResource(R.string.restore_intro), style = MaterialTheme.typography.bodyMedium)
        if (busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.restore_working))
            return@BackupLayout
        }
        OutlinedButton(onClick = {
            session.expectExternal()
            open.launch(arrayOf("*/*"))
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_choose)) }
        if (file != null) {
            Text(stringResource(R.string.restore_chosen), style = MaterialTheme.typography.bodySmall)
            PasswordField(stringResource(R.string.backup_password), password, { password = it; message = null }, "restorePassword")
            Button(onClick = { restore(replace = false) }, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("restoreAdd")) {
                Text(stringResource(R.string.restore_add))
            }
            OutlinedButton(onClick = { confirmReplace = true }, enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore_replace))
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("restoreMessage")) }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.restore_replace_title)) },
            text = { Text(stringResource(R.string.restore_replace_message)) },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; restore(replace = true) }) { Text(stringResource(R.string.restore_replace)) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

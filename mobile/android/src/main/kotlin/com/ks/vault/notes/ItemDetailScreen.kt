package com.ks.vault.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.ks.vault.auth.VaultSession
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.VaultRepository
import com.ks.vault.documents.AttachmentImport
import com.ks.vault.documents.AttachmentStrip
import com.ks.vault.ui.Clipboard
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route
import com.ks.vault.ui.formatDay
import com.ks.vault.ui.label
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(vault: VaultRepository, nav: Navigator, session: VaultSession, id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val item by remember(id) { vault.item(id) }.collectAsState(initial = null)
    val attachments by remember(id) { vault.attachments(id) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val errorTooLarge = stringResource(R.string.attach_too_large, AttachmentImport.MAX_MB.toString())
    val errorUnsupported = stringResource(R.string.attach_unsupported)
    val errorFailed = stringResource(R.string.attach_failed)

    fun import(uri: android.net.Uri?) {
        session.externalDone()
        if (uri == null) return
        importing = true
        scope.launch {
            val result = AttachmentImport.import(context, vault, id, uri)
            importing = false
            when (result) {
                AttachmentImport.Result.Ok -> Unit
                AttachmentImport.Result.TooLarge -> snackbar.showSnackbar(errorTooLarge)
                AttachmentImport.Result.Unsupported -> snackbar.showSnackbar(errorUnsupported)
                AttachmentImport.Result.Failed -> snackbar.showSnackbar(errorFailed)
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it) }

    val current = item
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(current?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { scope.launch { vault.setPinned(current.id, !current.pinned) } }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(if (current.pinned) R.string.item_unpin else R.string.item_pin),
                                tint = if (current.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                        IconButton(onClick = { nav.go(Route.Edit(current.id, current.kind)) }, modifier = Modifier.testTag("edit")) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.item_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete")) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(current.kind.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            fields(current).forEach { (label, value) -> FieldRow(label, value) { Clipboard.copySensitive(context, value) } }
            if (current.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.field_notes), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(current.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("notes"))
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.attach_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            AttachmentStrip(vault, attachments) { nav.go(Route.Viewer(it.id)) }
            Spacer(Modifier.height(8.dp))
            if (importing) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        session.expectExternal()
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.attach_add_photo)) }
                    OutlinedButton(onClick = {
                        session.expectExternal()
                        pickFile.launch(arrayOf("application/pdf", "image/*"))
                    }) { Text(stringResource(R.string.attach_add_file)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.attach_hint), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.common_confirm_delete_title, current.title)) },
            text = { Text(stringResource(R.string.common_confirm_cannot_undo)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vault.delete(current)
                        nav.back()
                    }
                }, modifier = Modifier.testTag("confirmDelete")) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
        }
        TextButton(onClick = onCopy) { Text(stringResource(R.string.common_copy)) }
    }
}

/** The filled-in fields of an item, as label and value. */
@Composable
private fun fields(item: Item): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    fun add(label: String, value: String?) { if (!value.isNullOrBlank()) out += label to value }
    when (item.kind) {
        ItemKind.NOTE -> Unit
        ItemKind.ID_CARD -> {
            add(stringResource(R.string.field_id_type), item.idType?.label())
            add(stringResource(R.string.field_id_number), item.idNumber)
            add(stringResource(R.string.field_id_name), item.idName)
            add(stringResource(R.string.field_id_issued), item.idIssued?.let { formatDay(it) })
            add(stringResource(R.string.field_id_expires), item.idExpires?.let { formatDay(it) })
        }
        ItemKind.TICKET -> {
            add(stringResource(R.string.field_ticket_type), item.ticketType?.label())
            add(stringResource(R.string.field_from), item.fromPlace)
            add(stringResource(R.string.field_to), item.toPlace)
            add(stringResource(R.string.field_date), item.travelDate?.let { formatDay(it) })
            add(stringResource(R.string.field_time), item.travelTime)
            add(stringResource(R.string.field_booking_ref), item.bookingRef)
            add(stringResource(R.string.field_seat), item.seat)
        }
    }
    return out
}

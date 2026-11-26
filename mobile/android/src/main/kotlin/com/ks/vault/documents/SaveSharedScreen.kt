package com.ks.vault.documents

import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.VaultSession
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route
import com.ks.vault.ui.label
import kotlinx.coroutines.launch

/** "Save to KS Vault": the shared photos and PDFs become a new ticket, ID card or note. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SaveSharedScreen(vault: VaultRepository, nav: Navigator, session: VaultSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uris by session.shared.collectAsState()
    val firstName = remember(uris) {
        uris.firstOrNull()?.let { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }?.substringBeforeLast('.').orEmpty()
    }
    var kind by remember { mutableStateOf(ItemKind.TICKET) }
    var title by remember(firstName) { mutableStateOf(firstName) }
    var busy by remember { mutableStateOf(false) }
    var failedCount by remember { mutableStateOf(0) }

    fun close() {
        session.clearShared()
        if (nav.current == Route.SaveShared) nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = { close() }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pluralStringResource(R.plurals.share_files, uris.size, uris.size.toString()), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.share_kind), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ItemKind.TICKET, ItemKind.ID_CARD, ItemKind.NOTE).forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label()) })
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("shareTitle"),
            )
            if (failedCount > 0) {
                Text(pluralStringResource(R.plurals.share_failed, failedCount, failedCount.toString()), color = MaterialTheme.colorScheme.error)
            }
            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        busy = true
                        val files = uris
                        scope.launch {
                            val item = Item(kind = kind, title = title.trim().ifEmpty { kind.name.lowercase() })
                            val id = vault.save(item)
                            val failed = files.count { AttachmentImport.import(context, vault, id, it) != AttachmentImport.Result.Ok }
                            busy = false
                            if (failed == files.size) {
                                // Nothing could be read: no empty item is left behind; the error stays on screen.
                                vault.delete(item.copy(id = id))
                                failedCount = failed
                                return@launch
                            }
                            session.clearShared()
                            nav.replace(Route.Detail(id))
                        }
                    },
                    enabled = uris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("shareSave"),
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

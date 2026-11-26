package com.ks.vault.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.auth.VaultSession
import com.ks.vault.data.ItemKind
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route
import com.ks.vault.ui.icon
import com.ks.vault.ui.label
import com.ks.vault.ui.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    vault: VaultRepository,
    nav: Navigator,
    session: VaultSession,
    selectedId: Long? = null,
    onOpen: (Long) -> Unit = { nav.go(Route.Detail(it)) },
) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ItemKind?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val items by remember(kind, query) { vault.items(kind, query) }.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { session.lock() }, modifier = Modifier.testTag("lock")) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.list_lock_now))
                    }
                    IconButton(onClick = { nav.go(Route.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.common_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                // A plain FAB with an icon and a label: the label reaches TalkBack as the button's text.
                FloatingActionButton(onClick = { addMenu = true }, modifier = Modifier.testTag("add")) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_add))
                    }
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    ItemKind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.label()) },
                            leadingIcon = { Icon(k.icon(), contentDescription = null) },
                            onClick = {
                                addMenu = false
                                nav.go(Route.Edit(null, k))
                            },
                            modifier = Modifier.testTag("add_${k.name}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.list_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.list_search_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("search"),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(stringResource(R.string.list_filter_all)) })
                ItemKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label()) })
                }
            }
            val list = items
            if (list != null && list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (query.isBlank() && kind == null) R.string.list_empty else R.string.list_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else if (list != null) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val s = item.summary()
                                if (s.isNotEmpty()) Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = { Icon(item.kind.icon(), contentDescription = item.kind.label()) },
                            trailingContent = {
                                if (item.pinned) Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.item_pinned), tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = if (item.id == selectedId) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                            modifier = Modifier.clickable { onOpen(item.id) }.testTag("item"),
                        )
                    }
                }
            }
        }
    }
}

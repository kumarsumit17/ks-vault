package com.ks.vault.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.data.IdType
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.TicketType
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.Route
import com.ks.vault.ui.formatDay
import com.ks.vault.ui.label
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Adding ([id] null) or changing an item of [kind]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(vault: VaultRepository, nav: Navigator, id: Long?, kind: ItemKind) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    var draft by remember { mutableStateOf(Item(kind = kind, title = "")) }
    var titleMissing by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            vault.item(id).first()?.let { draft = it }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == null) R.string.edit_new_title else R.string.edit_title, kind.label())) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    TextButton(
                        enabled = loaded,
                        onClick = {
                            val d = draft.copy(title = draft.title.trim())
                            if (d.title.isEmpty()) {
                                titleMissing = true
                                return@TextButton
                            }
                            scope.launch {
                                val saved = vault.save(d)
                                if (id == null) nav.replace(Route.Detail(saved)) else nav.back()
                            }
                        },
                        modifier = Modifier.testTag("save"),
                    ) { Text(stringResource(R.string.common_save)) }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(
                label = stringResource(R.string.field_title),
                value = draft.title,
                onChange = { draft = draft.copy(title = it); titleMissing = false },
                error = if (titleMissing) stringResource(R.string.edit_title_missing) else null,
                tag = "title",
                capitalize = true,
            )
            when (kind) {
                ItemKind.NOTE -> Unit
                ItemKind.ID_CARD -> {
                    Label(stringResource(R.string.field_id_type))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdType.entries.forEach { t ->
                            FilterChip(selected = draft.idType == t, onClick = { draft = draft.copy(idType = t) }, label = { Text(t.label()) })
                        }
                    }
                    Field(stringResource(R.string.field_id_number), draft.idNumber.orEmpty(), { draft = draft.copy(idNumber = it) }, tag = "idNumber")
                    Field(stringResource(R.string.field_id_name), draft.idName.orEmpty(), { draft = draft.copy(idName = it) }, capitalize = true)
                    DateField(stringResource(R.string.field_id_issued), draft.idIssued) { draft = draft.copy(idIssued = it) }
                    DateField(stringResource(R.string.field_id_expires), draft.idExpires) { draft = draft.copy(idExpires = it) }
                }
                ItemKind.TICKET -> {
                    Label(stringResource(R.string.field_ticket_type))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TicketType.entries.forEach { t ->
                            FilterChip(selected = draft.ticketType == t, onClick = { draft = draft.copy(ticketType = t) }, label = { Text(t.label()) })
                        }
                    }
                    Field(stringResource(R.string.field_from), draft.fromPlace.orEmpty(), { draft = draft.copy(fromPlace = it) }, capitalize = true)
                    Field(stringResource(R.string.field_to), draft.toPlace.orEmpty(), { draft = draft.copy(toPlace = it) }, capitalize = true)
                    DateField(stringResource(R.string.field_date), draft.travelDate) { draft = draft.copy(travelDate = it) }
                    Field(stringResource(R.string.field_time), draft.travelTime.orEmpty(), { draft = draft.copy(travelTime = it) }, hint = stringResource(R.string.field_time_hint))
                    Field(stringResource(R.string.field_booking_ref), draft.bookingRef.orEmpty(), { draft = draft.copy(bookingRef = it) }, tag = "bookingRef")
                    Field(stringResource(R.string.field_seat), draft.seat.orEmpty(), { draft = draft.copy(seat = it) })
                }
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = if (kind == ItemKind.NOTE) 8 else 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("notesField"),
            )
            if (id == null) {
                Text(stringResource(R.string.edit_attach_later), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    tag: String? = null,
    hint: String? = null,
    capitalize: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}

/** A date chosen with the date picker; days since 1970, or null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: Long?, onChange: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value?.let { formatDay(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                if (value != null) {
                    IconButton(onClick = { onChange(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_clear_date))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // The read-only field does not take clicks itself; this layer opens the picker (not over the clear button).
        Box(Modifier.matchParentSize().padding(end = if (value != null) 48.dp else 0.dp).clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.let { it * DAY_MS })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, DAY_MS)) }
                    open = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}

private const val DAY_MS = 86_400_000L

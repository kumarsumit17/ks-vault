package com.ks.vault.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ks.vault.R
import com.ks.vault.data.IdType
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.TicketType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun ItemKind.icon(): ImageVector = when (this) {
    ItemKind.NOTE -> Icons.Filled.Create
    ItemKind.ID_CARD -> Icons.Filled.AccountBox
    ItemKind.TICKET -> Icons.Filled.DateRange
}

@Composable
fun ItemKind.label(): String = stringResource(
    when (this) {
        ItemKind.NOTE -> R.string.kind_note
        ItemKind.ID_CARD -> R.string.kind_id_card
        ItemKind.TICKET -> R.string.kind_ticket
    },
)

@Composable
fun IdType.label(): String = stringResource(
    when (this) {
        IdType.AADHAAR -> R.string.id_type_aadhaar
        IdType.PAN -> R.string.id_type_pan
        IdType.PASSPORT -> R.string.id_type_passport
        IdType.DRIVING_LICENCE -> R.string.id_type_driving_licence
        IdType.VOTER_ID -> R.string.id_type_voter_id
        IdType.OTHER -> R.string.id_type_other
    },
)

@Composable
fun TicketType.label(): String = stringResource(
    when (this) {
        TicketType.TRAIN -> R.string.ticket_type_train
        TicketType.FLIGHT -> R.string.ticket_type_flight
        TicketType.BUS -> R.string.ticket_type_bus
        TicketType.EVENT -> R.string.ticket_type_event
        TicketType.OTHER -> R.string.ticket_type_other
    },
)

/** A date (days since 1970) in the interface language's medium style, always with Latin digits. */
fun formatDay(epochDay: Long, locale: Locale = Locale.getDefault()): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/** The second line of an item in the list. */
@Composable
fun Item.summary(): String = when (kind) {
    ItemKind.NOTE -> notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    ItemKind.ID_CARD -> listOfNotNull(idType?.label(), idNumber?.takeIf { it.isNotBlank() }).joinToString(" · ")
    ItemKind.TICKET -> listOfNotNull(
        listOfNotNull(fromPlace?.takeIf { it.isNotBlank() }, toPlace?.takeIf { it.isNotBlank() }).joinToString(" → ").ifEmpty { null },
        travelDate?.let { formatDay(it) },
    ).joinToString(" · ")
}

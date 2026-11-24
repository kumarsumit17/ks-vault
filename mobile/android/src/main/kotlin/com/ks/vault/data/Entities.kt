package com.ks.vault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemKind { NOTE, ID_CARD, TICKET }

/** Kinds of ID card. Stored by name; shown through the language files. */
enum class IdType { AADHAAR, PAN, PASSPORT, DRIVING_LICENCE, VOTER_ID, OTHER }

/** Kinds of ticket. */
enum class TicketType { TRAIN, FLIGHT, BUS, EVENT, OTHER }

/**
 * One thing in the vault: a note, an ID card or a ticket. The fields of the other kinds stay null.
 * Dates are days since 1970-01-01 (`LocalDate.toEpochDay`).
 */
@Entity(tableName = "items", indices = [Index("kind"), Index("updatedAt")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ItemKind,
    val title: String,
    val notes: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    // ID card
    val idType: IdType? = null,
    val idNumber: String? = null,
    val idName: String? = null,
    val idIssued: Long? = null,
    val idExpires: Long? = null,
    // Ticket
    val ticketType: TicketType? = null,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val travelDate: Long? = null,
    val travelTime: String? = null,
    val bookingRef: String? = null,
    val seat: String? = null,
)

/**
 * A file on an item. Its bytes are in an encrypted file ([fileName]) under its own key ([fileKey]).
 * A PDF's pages are drawn into images when it is added; each page is an attachment whose [parentId]
 * is the PDF's id.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Attachment::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId"), Index("parentId")],
)
class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val parentId: Long? = null,
    val page: Int = 0,
    val name: String,
    val mime: String,
    val size: Long,
    val fileName: String,
    val fileKey: ByteArray,
    val createdAt: Long = 0,
) {
    val isPdf: Boolean get() = mime == "application/pdf"
    val isImage: Boolean get() = mime.startsWith("image/")
}

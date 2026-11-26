package com.ks.vault.backup

import com.ks.vault.crypto.Aead
import com.ks.vault.crypto.DamagedStreamException
import com.ks.vault.crypto.Kdf
import com.ks.vault.crypto.OpeningInputStream
import com.ks.vault.crypto.SealingOutputStream
import com.ks.vault.crypto.wipe
import com.ks.vault.data.IdType
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.TicketType
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException

/**
 * The backup file (`.ksvault`), specs/backup.md:
 *
 * ```
 * "KSVB" || format (1) || rounds (4) || salt (16) || chunked AES-256-GCM stream (key = PBKDF2(backup password, salt))
 * ```
 *
 * The stream holds records: items, then attachments (a PDF before its pages), then an end mark.
 * It needs only the backup password, never this phone's Keystore, so it restores on a new phone.
 */
object BackupFormat {
    val MAGIC = "KSVB".toByteArray()
    const val FORMAT = 1
    const val MIN_PASSWORD = 8
    internal const val ITEM = 1
    internal const val ATTACHMENT = 2
    internal const val END = 0
}

/** An attachment as it is in the file; ids are the ones it had in the vault it came from. */
class BackupAttachment(
    val id: Long,
    val itemId: Long,
    val parentId: Long?,
    val page: Int,
    val name: String,
    val mime: String,
    val createdAt: Long,
)

class WrongBackupPasswordException : Exception()

class BackupWriter(out: OutputStream, password: CharArray, iterations: Int = Kdf.ITERATIONS) : Closeable {
    private val data: DataOutputStream

    init {
        val salt = Aead.randomBytes(Kdf.SALT_BYTES)
        val header = DataOutputStreamBytes {
            write(BackupFormat.MAGIC); writeByte(BackupFormat.FORMAT); writeInt(iterations); write(salt)
        }
        out.write(header)
        val key = Kdf.derive(password, salt, iterations)
        data = DataOutputStream(SealingOutputStream(out, key.copyOf(), header).buffered())
        key.wipe()
    }

    fun item(i: Item) = with(data) {
        writeByte(BackupFormat.ITEM)
        writeLong(i.id); str(i.kind.name); str(i.title); str(i.notes); writeBoolean(i.pinned)
        writeLong(i.createdAt); writeLong(i.updatedAt)
        optStr(i.idType?.name); optStr(i.idNumber); optStr(i.idName); optLong(i.idIssued); optLong(i.idExpires)
        optStr(i.ticketType?.name); optStr(i.fromPlace); optStr(i.toPlace); optLong(i.travelDate)
        optStr(i.travelTime); optStr(i.bookingRef); optStr(i.seat)
    }

    fun attachment(a: BackupAttachment, bytes: ByteArray) = with(data) {
        writeByte(BackupFormat.ATTACHMENT)
        writeLong(a.id); writeLong(a.itemId); optLong(a.parentId); writeInt(a.page)
        str(a.name); str(a.mime); writeLong(a.createdAt)
        writeInt(bytes.size); write(bytes)
    }

    override fun close() {
        data.writeByte(BackupFormat.END)
        data.close()
    }

    private fun DataOutputStream.str(s: String) { val b = s.toByteArray(); writeInt(b.size); write(b) }
    private fun DataOutputStream.optStr(s: String?) { writeBoolean(s != null); if (s != null) str(s) }
    private fun DataOutputStream.optLong(v: Long?) { writeBoolean(v != null); if (v != null) writeLong(v) }
}

class BackupReader(input: InputStream, password: CharArray) : Closeable {
    private val data: DataInputStream

    init {
        val head = DataInputStream(input)
        val magic = ByteArray(4)
        try {
            head.readFully(magic)
            if (!magic.contentEquals(BackupFormat.MAGIC) || head.readByte().toInt() != BackupFormat.FORMAT) {
                throw DamagedStreamException("Not a KS Vault backup")
            }
        } catch (_: EOFException) {
            throw DamagedStreamException("Not a KS Vault backup")
        }
        val iterations = head.readInt()
        if (iterations !in 1..10_000_000) throw DamagedStreamException("Not a KS Vault backup")
        val salt = ByteArray(Kdf.SALT_BYTES).also(head::readFully)
        val header = DataOutputStreamBytes {
            write(BackupFormat.MAGIC); writeByte(BackupFormat.FORMAT); writeInt(iterations); write(salt)
        }
        val key = Kdf.derive(password, salt, iterations)
        data = DataInputStream(OpeningInputStream(head, key.copyOf(), header).buffered())
        key.wipe()
    }

    /** Reads every record. Throws [WrongBackupPasswordException] or [DamagedStreamException]. */
    suspend fun read(onItem: suspend (Item) -> Unit, onAttachment: suspend (BackupAttachment, ByteArray) -> Unit) {
        try {
            while (true) {
                when (data.readByte().toInt()) {
                    BackupFormat.END -> {
                        if (data.read() != -1) throw DamagedStreamException("Data after the end")
                        return
                    }
                    BackupFormat.ITEM -> onItem(readItem())
                    BackupFormat.ATTACHMENT -> {
                        val a = BackupAttachment(
                            id = data.readLong(), itemId = data.readLong(), parentId = optLong(), page = data.readInt(),
                            name = str(), mime = str(), createdAt = data.readLong(),
                        )
                        val size = data.readInt()
                        if (size < 0 || size > 64 * 1024 * 1024) throw DamagedStreamException("Bad attachment size")
                        val bytes = ByteArray(size).also(data::readFully)
                        onAttachment(a, bytes)
                        bytes.fill(0)
                    }
                    else -> throw DamagedStreamException("Unknown record")
                }
            }
        } catch (e: AEADBadTagException) {
            // The first chunk failing is a wrong password; a later one is a changed file. The first
            // record is read from the first chunk, so a wrong password always fails before anything is used.
            throw if (firstRecord) WrongBackupPasswordException() else DamagedStreamException("The file was changed")
        } catch (_: EOFException) {
            throw DamagedStreamException("The file is cut off")
        } finally {
            firstRecord = false
        }
    }

    private var firstRecord = true

    private fun readItem(): Item {
        firstRecord = false
        return Item(
            id = data.readLong(), kind = ItemKind.valueOf(str()), title = str(), notes = str(), pinned = data.readBoolean(),
            createdAt = data.readLong(), updatedAt = data.readLong(),
            idType = optStr()?.let { IdType.valueOf(it) }, idNumber = optStr(), idName = optStr(), idIssued = optLong(), idExpires = optLong(),
            ticketType = optStr()?.let { TicketType.valueOf(it) }, fromPlace = optStr(), toPlace = optStr(), travelDate = optLong(),
            travelTime = optStr(), bookingRef = optStr(), seat = optStr(),
        )
    }

    private fun str(): String {
        val n = data.readInt()
        if (n < 0 || n > 16 * 1024 * 1024) throw DamagedStreamException("Bad text length")
        return String(ByteArray(n).also(data::readFully))
    }
    private fun optStr(): String? = if (data.readBoolean()) str() else null
    private fun optLong(): Long? = if (data.readBoolean()) data.readLong() else null

    override fun close() = data.close()
}

private inline fun DataOutputStreamBytes(block: DataOutputStream.() -> Unit): ByteArray {
    val bytes = java.io.ByteArrayOutputStream()
    DataOutputStream(bytes).use(block)
    return bytes.toByteArray()
}

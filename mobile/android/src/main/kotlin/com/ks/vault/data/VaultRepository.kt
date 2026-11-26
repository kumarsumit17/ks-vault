package com.ks.vault.data

import androidx.room.withTransaction
import com.ks.vault.backup.BackupAttachment
import com.ks.vault.backup.BackupReader
import com.ks.vault.backup.BackupWriter
import com.ks.vault.crypto.FileCipher
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Everything the screens read and change in an unlocked vault. */
class VaultRepository(private val db: VaultDatabase, private val files: FileCipher, private val now: () -> Long = System::currentTimeMillis) {
    private val dao = db.dao()

    fun items(kind: ItemKind?, query: String): Flow<List<Item>> = dao.items(kind, Search.likePattern(query))

    fun item(id: Long): Flow<Item?> = dao.item(id)

    fun attachments(itemId: Long): Flow<List<Attachment>> = dao.attachments(itemId)

    /** Saves a new item (id 0) or changes an existing one. Returns its id. */
    suspend fun save(item: Item): Long {
        val t = now()
        return if (item.id == 0L) {
            dao.insert(item.copy(createdAt = t, updatedAt = t))
        } else {
            dao.update(item.copy(updatedAt = t))
            item.id
        }
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(item: Item) = withContext(Dispatchers.IO) {
        val attached = dao.allAttachmentsOf(item.id)
        dao.delete(item)
        attached.forEach { files.delete(it.fileName) }
    }

    /**
     * Adds a file to an item: [bytes] is encrypted into its own file, and for a PDF each of [pages]
     * (JPEG images of its pages) too.
     */
    suspend fun addAttachment(itemId: Long, name: String, mime: String, bytes: ByteArray, pages: List<ByteArray> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            val t = now()
            val stored = files.write(bytes)
            val id = dao.insert(
                Attachment(itemId = itemId, name = name, mime = mime, size = bytes.size.toLong(), fileName = stored.fileName, fileKey = stored.key, createdAt = t),
            )
            pages.forEachIndexed { i, page ->
                val p = files.write(page)
                dao.insert(
                    Attachment(
                        itemId = itemId, parentId = id, page = i, name = "$name #${i + 1}", mime = "image/jpeg",
                        size = page.size.toLong(), fileName = p.fileName, fileKey = p.key, createdAt = t,
                    ),
                )
            }
            id
        }

    suspend fun attachment(id: Long): Attachment? = dao.attachment(id)

    suspend fun pages(pdf: Attachment): List<Attachment> = dao.pages(pdf.id)

    /** The plain bytes, in memory only. */
    suspend fun read(attachment: Attachment): ByteArray = withContext(Dispatchers.IO) {
        files.read(attachment.fileName, attachment.fileKey)
    }

    suspend fun deleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        val all = dao.attachmentWithPages(attachment.id)
        dao.deleteAttachment(attachment.id)
        all.forEach { files.delete(it.fileName) }
    }

    /** Removes encrypted files that no attachment refers to (left behind if the app was killed while saving). */
    suspend fun removeStrayFiles() = withContext(Dispatchers.IO) {
        files.deleteAllExcept(dao.allFileNames().toSet())
    }

    /** Writes every item and attachment to [out] as a backup file sealed with [password]. Returns the item count. */
    suspend fun backup(out: OutputStream, password: CharArray): Int = withContext(Dispatchers.IO) {
        val items = dao.allItems()
        BackupWriter(out, password).use { w ->
            items.forEach(w::item)
            dao.allAttachments().forEach { a ->
                val bytes = files.read(a.fileName, a.fileKey)
                w.attachment(BackupAttachment(a.id, a.itemId, a.parentId, a.page, a.name, a.mime, a.createdAt), bytes)
                bytes.fill(0)
            }
        }
        items.size
    }

    /**
     * Restores a backup: adds its items to the vault, or first removes everything ([replace]). It all
     * happens in one transaction: if the password is wrong or the file is damaged, nothing changes.
     * Returns the number of items restored.
     */
    suspend fun restore(input: InputStream, password: CharArray, replace: Boolean): Int = withContext(Dispatchers.IO) {
        val reader = BackupReader(input, password)
        val oldFiles = if (replace) dao.allFileNames() else emptyList()
        val newFiles = mutableListOf<String>()
        var count = 0
        try {
            db.withTransaction {
                if (replace) dao.deleteAllItems()
                val itemIds = HashMap<Long, Long>()
                val attachmentIds = HashMap<Long, Long>()
                reader.read(
                    onItem = { item ->
                        itemIds[item.id] = dao.insert(item.copy(id = 0))
                        count++
                    },
                    onAttachment = { a, bytes ->
                        val itemId = itemIds[a.itemId] ?: return@read
                        val stored = files.write(bytes)
                        newFiles += stored.fileName
                        attachmentIds[a.id] = dao.insert(
                            Attachment(
                                itemId = itemId, parentId = a.parentId?.let { attachmentIds[it] }, page = a.page, name = a.name,
                                mime = a.mime, size = bytes.size.toLong(), fileName = stored.fileName, fileKey = stored.key, createdAt = a.createdAt,
                            ),
                        )
                    },
                )
            }
        } catch (e: Throwable) {
            newFiles.forEach(files::delete)
            throw e
        } finally {
            reader.close()
        }
        oldFiles.forEach(files::delete)
        count
    }

    suspend fun itemCount(): Int = dao.itemCount()

    /** The expiry dates of every ID card, as they change. */
    fun expiryDays(): Flow<List<Long>> = dao.expiryDays()

    fun close() = db.close()
}

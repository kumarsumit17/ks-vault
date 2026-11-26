package com.ks.vault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    /** [like] is a LIKE pattern made by [Search.likePattern]; [like] null lists everything. */
    @Query(
        """
        SELECT * FROM items
        WHERE (:kind IS NULL OR kind = :kind)
          AND (:like IS NULL
               OR title LIKE :like ESCAPE '\' OR notes LIKE :like ESCAPE '\'
               OR idNumber LIKE :like ESCAPE '\' OR idName LIKE :like ESCAPE '\'
               OR fromPlace LIKE :like ESCAPE '\' OR toPlace LIKE :like ESCAPE '\'
               OR bookingRef LIKE :like ESCAPE '\' OR seat LIKE :like ESCAPE '\'
               OR travelTime LIKE :like ESCAPE '\')
        ORDER BY pinned DESC, updatedAt DESC, id DESC
        """,
    )
    fun items(kind: ItemKind?, like: String?): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun item(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun itemNow(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("UPDATE items SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT * FROM attachments WHERE itemId = :itemId AND parentId IS NULL ORDER BY id")
    fun attachments(itemId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE itemId = :itemId")
    suspend fun allAttachmentsOf(itemId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun attachment(id: Long): Attachment?

    @Query("SELECT * FROM attachments WHERE parentId = :parentId ORDER BY page")
    suspend fun pages(parentId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id OR parentId = :id")
    suspend fun attachmentWithPages(id: Long): List<Attachment>

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Query("SELECT * FROM items ORDER BY id")
    suspend fun allItems(): List<Item>

    /** By id, so a PDF comes before its pages. */
    @Query("SELECT * FROM attachments ORDER BY id")
    suspend fun allAttachments(): List<Attachment>

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("SELECT idExpires FROM items WHERE kind = 'ID_CARD' AND idExpires IS NOT NULL")
    fun expiryDays(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun itemCount(): Int

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}

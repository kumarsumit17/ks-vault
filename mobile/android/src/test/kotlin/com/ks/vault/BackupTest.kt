package com.ks.vault

import com.ks.vault.backup.BackupAttachment
import com.ks.vault.backup.BackupReader
import com.ks.vault.backup.BackupWriter
import com.ks.vault.backup.WrongBackupPasswordException
import com.ks.vault.crypto.Aead
import com.ks.vault.crypto.DamagedStreamException
import com.ks.vault.crypto.OpeningInputStream
import com.ks.vault.crypto.SealingOutputStream
import com.ks.vault.data.IdType
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

class BackupTest {
    private val rounds = 1_000

    private fun seal(plain: ByteArray, key: ByteArray, header: ByteArray): ByteArray =
        ByteArrayOutputStream().also { o -> SealingOutputStream(o, key, header).use { it.write(plain) } }.toByteArray()

    private fun open(sealed: ByteArray, key: ByteArray, header: ByteArray): ByteArray =
        OpeningInputStream(ByteArrayInputStream(sealed), key, header).use { it.readBytes() }

    @Test fun chunkedRoundTripOfSeveralChunks() {
        val key = Aead.randomKey()
        val plain = ByteArray(200_000) { (it % 251).toByte() } // four chunks
        val sealed = seal(plain, key, "h".toByteArray())
        assertArrayEquals(plain, open(sealed, key, "h".toByteArray()))
        assertFalse(sealed.toList().windowed(64, 4096).any { it.toByteArray().contentEquals(plain.copyOfRange(0, 64)) })
    }

    @Test fun chunkedEmptyStream() {
        val key = Aead.randomKey()
        assertEquals(0, open(seal(ByteArray(0), key, byteArrayOf()), key, byteArrayOf()).size)
    }

    @Test fun cutOffStreamIsRejected() {
        val key = Aead.randomKey()
        val sealed = seal(ByteArray(150_000) { 7 }, key, byteArrayOf())
        // Drop the last chunk: the reader must notice that the end mark is missing.
        val firstChunkLen = 5 + ((sealed[1].toInt() and 0xff) shl 24 or ((sealed[2].toInt() and 0xff) shl 16) or ((sealed[3].toInt() and 0xff) shl 8) or (sealed[4].toInt() and 0xff))
        try {
            open(sealed.copyOf(firstChunkLen * 2), key, byteArrayOf())
            fail("expected DamagedStreamException")
        } catch (_: DamagedStreamException) {
        }
    }

    @Test fun otherHeaderOrKeyIsRejected() {
        val key = Aead.randomKey()
        val sealed = seal(byteArrayOf(1, 2, 3), key, "a".toByteArray())
        try { open(sealed, key, "b".toByteArray()); fail() } catch (_: AEADBadTagException) {}
        try { open(sealed, Aead.randomKey(), "a".toByteArray()); fail() } catch (_: AEADBadTagException) {}
    }

    private fun backup(password: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupWriter(out, password.toCharArray(), rounds).use { w ->
            w.item(Item(id = 7, kind = ItemKind.ID_CARD, title = "PAN", idType = IdType.PAN, idNumber = "ABCDE1234F", idExpires = 20_000, createdAt = 1, updatedAt = 2))
            w.item(Item(id = 9, kind = ItemKind.NOTE, title = "Note", notes = "हिंदी नोट\nline 2", pinned = true))
            w.attachment(BackupAttachment(3, 7, null, 0, "card.pdf", "application/pdf", 5), ByteArray(100_000) { 1 })
            w.attachment(BackupAttachment(4, 7, 3, 0, "card.pdf #1", "image/jpeg", 5), byteArrayOf(9, 9))
        }
        return out.toByteArray()
    }

    @Test fun backupRoundTrip() = runBlocking {
        val file = backup("backup-pass")
        assertFalse(String(file, Charsets.ISO_8859_1).contains("ABCDE1234F"))
        val items = mutableListOf<Item>()
        val atts = mutableListOf<Pair<BackupAttachment, Int>>()
        BackupReader(ByteArrayInputStream(file), "backup-pass".toCharArray()).use { r ->
            r.read({ items += it }, { a, b -> atts += a to b.size })
        }
        assertEquals(listOf(7L, 9L), items.map { it.id })
        assertEquals("ABCDE1234F", items[0].idNumber)
        assertEquals(IdType.PAN, items[0].idType)
        assertEquals(20_000L, items[0].idExpires)
        assertEquals("हिंदी नोट\nline 2", items[1].notes)
        assertEquals(true, items[1].pinned)
        assertEquals(listOf(100_000, 2), atts.map { it.second })
        assertEquals(3L, atts[1].first.parentId)
    }

    @Test fun wrongBackupPassword() = runBlocking {
        val file = backup("backup-pass")
        try {
            BackupReader(ByteArrayInputStream(file), "other-pass".toCharArray()).use { it.read({}, { _, _ -> }) }
            fail("expected WrongBackupPasswordException")
        } catch (_: WrongBackupPasswordException) {
        }
    }

    @Test fun changedOrCutBackupIsRejected() = runBlocking {
        val file = backup("backup-pass")
        val changed = file.copyOf().also { it[it.size - 40] = (it[it.size - 40] + 1).toByte() }
        for (bad in listOf(changed, file.copyOf(file.size - 30), "not a backup at all".toByteArray())) {
            try {
                BackupReader(ByteArrayInputStream(bad), "backup-pass".toCharArray()).use { it.read({}, { _, _ -> }) }
                fail("expected DamagedStreamException")
            } catch (_: DamagedStreamException) {
            }
        }
    }
}

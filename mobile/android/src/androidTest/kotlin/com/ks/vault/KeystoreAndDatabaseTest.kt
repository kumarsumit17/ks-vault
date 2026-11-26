package com.ks.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ks.vault.crypto.Aead
import com.ks.vault.crypto.FileCipher
import com.ks.vault.crypto.KeystoreWrapper
import com.ks.vault.crypto.SecretKind
import com.ks.vault.crypto.VaultKeys
import com.ks.vault.crypto.VaultUnreadableException
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.VaultDatabase
import com.ks.vault.data.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeystoreAndDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alias = "ks_vault_test_key"
    private val dbName = "test-vault.db"
    private val dir = File(context.cacheDir, "vault-test").apply { mkdirs() }

    @After fun cleanUp() {
        KeystoreWrapper(alias).destroy()
        context.deleteDatabase(dbName)
        dir.deleteRecursively()
    }

    @Test fun keystoreWrapsAndUnwraps() {
        val wrapper = KeystoreWrapper(alias)
        val secret = Aead.randomKey()
        val wrapped = wrapper.wrap(secret)
        assertFalse(wrapped.contentEquals(secret))
        assertArrayEquals(secret, wrapper.unwrap(wrapped))
    }

    @Test fun vaultKeysWithTheRealKeystore() {
        val keys = VaultKeys(File(dir, "vault.key"), KeystoreWrapper(alias), iterations = 10_000)
        val vaultKey = keys.create("password1".toCharArray(), SecretKind.PASSWORD)
        assertArrayEquals(vaultKey, keys.unlock("password1".toCharArray()))
        assertNull(keys.unlock("password2".toCharArray()))
        // Without the Keystore key the file cannot be opened, even with the right secret.
        KeystoreWrapper(alias).destroy()
        try {
            keys.unlock("password1".toCharArray())
            fail("expected VaultUnreadableException")
        } catch (_: VaultUnreadableException) {
        }
    }

    @Test fun databaseIsEncryptedAndNeedsTheKey() = runBlocking {
        val key = Aead.randomKey()
        val repo = VaultRepository(VaultDatabase.open(context, key, dbName), FileCipher(dir))
        val id = repo.save(Item(kind = ItemKind.TICKET, title = "Delhi to Pune", bookingRef = "PNR4242424242"))
        assertEquals("PNR4242424242", repo.item(id).first()?.bookingRef)
        assertEquals(1, repo.items(null, "4242").first().size)
        assertEquals(0, repo.items(ItemKind.NOTE, "").first().size)
        repo.close()

        // The file on disk has no plain text in it.
        val raw = context.getDatabasePath(dbName).readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("PNR4242424242"))
        assertFalse(String(raw, Charsets.ISO_8859_1).startsWith("SQLite format 3"))

        // A wrong key does not open it.
        try {
            VaultDatabase.open(context, Aead.randomKey(), dbName).close()
            fail("expected the wrong key to fail")
        } catch (_: Exception) {
        }

        // The right key opens it again.
        val again = VaultRepository(VaultDatabase.open(context, key, dbName), FileCipher(dir))
        assertEquals("Delhi to Pune", again.item(id).first()?.title)
        again.close()
    }

    @Test fun backupRestoresIntoAnotherVault() = runBlocking {
        val repo = VaultRepository(VaultDatabase.open(context, Aead.randomKey(), dbName), FileCipher(dir))
        val id = repo.save(Item(kind = ItemKind.TICKET, title = "Pune trip", bookingRef = "4521367890"))
        repo.addAttachment(id, "t.pdf", "application/pdf", byteArrayOf(1, 2), pages = listOf(byteArrayOf(3)))
        val file = java.io.ByteArrayOutputStream().also { repo.backup(it, "backup-pass".toCharArray()) }.toByteArray()
        repo.close()
        context.deleteDatabase(dbName)
        dir.deleteRecursively()

        // A new vault (another key) with one item of its own.
        val other = VaultRepository(VaultDatabase.open(context, Aead.randomKey(), dbName), FileCipher(dir))
        other.save(Item(kind = ItemKind.NOTE, title = "Mine"))
        // A wrong password changes nothing.
        try {
            other.restore(java.io.ByteArrayInputStream(file), "wrong-pass".toCharArray(), replace = true)
            fail("expected WrongBackupPasswordException")
        } catch (_: com.ks.vault.backup.WrongBackupPasswordException) {
        }
        assertEquals(1, other.itemCount())
        // Add keeps what is there; the PDF and its page come back, encrypted again with new keys.
        assertEquals(1, other.restore(java.io.ByteArrayInputStream(file), "backup-pass".toCharArray(), replace = false))
        assertEquals(2, other.itemCount())
        val restored = other.items(null, "4521367890").first().single()
        val pdf = other.attachments(restored.id).first().single()
        assertArrayEquals(byteArrayOf(1, 2), other.read(pdf))
        assertArrayEquals(byteArrayOf(3), other.read(other.pages(pdf).single()))
        // Replace leaves only the backup's items, and no stray files.
        other.restore(java.io.ByteArrayInputStream(file), "backup-pass".toCharArray(), replace = true)
        assertEquals(1, other.itemCount())
        assertEquals(2, dir.listFiles()!!.size)
        other.close()
    }

    @Test fun attachmentsAreEncryptedAndDeletedWithTheirItem() = runBlocking {
        val repo = VaultRepository(VaultDatabase.open(context, Aead.randomKey(), dbName), FileCipher(dir))
        val id = repo.save(Item(kind = ItemKind.ID_CARD, title = "PAN"))
        val photo = ByteArray(5_000) { (it % 200).toByte() }
        val attId = repo.addAttachment(id, "front.jpg", "image/jpeg", photo, pages = emptyList())
        val pdfId = repo.addAttachment(id, "card.pdf", "application/pdf", byteArrayOf(1, 2, 3), pages = listOf(byteArrayOf(9), byteArrayOf(8)))
        val a = repo.attachment(attId)!!
        assertArrayEquals(photo, repo.read(a))
        assertEquals(2, repo.attachments(id).first().size) // pages are not listed on their own
        assertEquals(2, repo.pages(repo.attachment(pdfId)!!).size)
        assertEquals(4, dir.listFiles()!!.size) // the photo, the PDF and its two pages
        repo.delete(repo.item(id).first()!!)
        assertEquals(0, dir.listFiles()!!.size)
        repo.close()
    }
}

package com.ks.vault

import com.ks.vault.crypto.Aead
import com.ks.vault.crypto.FileCipher
import com.ks.vault.crypto.Kdf
import com.ks.vault.crypto.KeyWrapper
import com.ks.vault.crypto.SecretKind
import com.ks.vault.crypto.VaultKeys
import com.ks.vault.crypto.VaultUnreadableException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.AEADBadTagException

/** A stand-in for the Android Keystore: a plain AES key in memory. */
class FakeWrapper : KeyWrapper {
    private var key: ByteArray? = Aead.randomKey()
    override fun wrap(plain: ByteArray) = Aead.seal(key ?: error("destroyed"), plain)
    override fun unwrap(wrapped: ByteArray) = Aead.open(key ?: error("destroyed"), wrapped)
    override fun destroy() { key = null }
}

class CryptoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fast = 1_000 // rounds for tests; the app uses Kdf.ITERATIONS

    @Test fun pbkdf2MatchesTheRfc7914Vector() {
        // RFC 7914, section 11: PBKDF2-HMAC-SHA256, P = "passwd", S = "salt", c = 1, dkLen = 64.
        val key = Kdf.derive("passwd".toCharArray(), "salt".toByteArray(), iterations = 1, bytes = 64)
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            key.joinToString("") { "%02x".format(it) },
        )
    }

    @Test fun appUsesAtLeastTheMinimumRounds() {
        assertTrue(Kdf.ITERATIONS >= 310_000)
    }

    @Test fun aeadRoundTripAndTamper() {
        val key = Aead.randomKey()
        val box = Aead.seal(key, "hello".toByteArray(), "aad".toByteArray())
        assertArrayEquals("hello".toByteArray(), Aead.open(key, box, "aad".toByteArray()))
        expectBadTag { Aead.open(Aead.randomKey(), box, "aad".toByteArray()) }
        expectBadTag { Aead.open(key, box, "other".toByteArray()) }
        expectBadTag { Aead.open(key, box.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }, "aad".toByteArray()) }
    }

    @Test fun eachSealUsesANewNonce() {
        val key = Aead.randomKey()
        assertFalse(Aead.seal(key, byteArrayOf(1)).contentEquals(Aead.seal(key, byteArrayOf(1))))
    }

    @Test fun createThenUnlockGivesTheSameKey() {
        val keys = VaultKeys(File(tmp.root, "vault.key"), FakeWrapper(), fast)
        assertFalse(keys.exists())
        val created = keys.create("secret1".toCharArray(), SecretKind.PASSWORD)
        assertEquals(SecretKind.PASSWORD, keys.kind())
        assertArrayEquals(created, keys.unlock("secret1".toCharArray()))
        assertNull(keys.unlock("secret2".toCharArray()))
    }

    @Test fun theKeyFileHoldsNoSecretAndNoVaultKey() {
        val file = File(tmp.root, "vault.key")
        val keys = VaultKeys(file, FakeWrapper(), fast)
        val vaultKey = keys.create("hunter22".toCharArray(), SecretKind.PASSWORD)
        val bytes = file.readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("hunter22"))
        assertFalse(bytes.toList().windowed(vaultKey.size).any { it.toByteArray().contentEquals(vaultKey) })
    }

    @Test fun changeKeepsTheVaultKeyAndDropsTheOldSecret() {
        val keys = VaultKeys(File(tmp.root, "vault.key"), FakeWrapper(), fast)
        val vaultKey = keys.create("old-secret".toCharArray(), SecretKind.PASSWORD)
        assertFalse(keys.change("wrong".toCharArray(), "1235".toCharArray(), SecretKind.PATTERN))
        assertTrue(keys.change("old-secret".toCharArray(), "1235".toCharArray(), SecretKind.PATTERN))
        assertEquals(SecretKind.PATTERN, keys.kind())
        assertNull(keys.unlock("old-secret".toCharArray()))
        assertArrayEquals(vaultKey, keys.unlock("1235".toCharArray()))
    }

    @Test fun anotherPhoneCannotOpenTheKeyFile() {
        val file = File(tmp.root, "vault.key")
        VaultKeys(file, FakeWrapper(), fast).create("secret1".toCharArray(), SecretKind.PASSWORD)
        // Same file and secret, but not this phone's Keystore key.
        try {
            VaultKeys(file, FakeWrapper(), fast).unlock("secret1".toCharArray())
            fail("expected VaultUnreadableException")
        } catch (_: VaultUnreadableException) {
        }
    }

    @Test fun eraseMakesTheVaultUnopenable() {
        val file = File(tmp.root, "vault.key")
        val keys = VaultKeys(file, FakeWrapper(), fast)
        keys.create("secret1".toCharArray(), SecretKind.PASSWORD)
        keys.erase()
        assertFalse(keys.exists())
        assertNull(keys.kind())
    }

    @Test fun aChangedHeaderIsRejected() {
        val file = File(tmp.root, "vault.key")
        val keys = VaultKeys(file, FakeWrapper(), fast)
        keys.create("secret1".toCharArray(), SecretKind.PASSWORD)
        val bytes = file.readBytes()
        bytes[5] = SecretKind.PATTERN.ordinal.toByte() // the kind byte, after "KSVK" and the format
        file.writeBytes(bytes)
        assertNull(keys.unlock("secret1".toCharArray()))
    }

    @Test fun fileCipherRoundTripAndNoPlainBytesOnDisk() {
        val dir = File(tmp.root, "attachments")
        val files = FileCipher(dir)
        val plain = ByteArray(10_000) { (it % 251).toByte() }
        val stored = files.write(plain)
        assertArrayEquals(plain, files.read(stored.fileName, stored.key))
        val onDisk = File(dir, stored.fileName).readBytes()
        assertFalse(onDisk.toList().windowed(64).any { it.toByteArray().contentEquals(plain.copyOfRange(0, 64)) })
        expectBadTag { files.read(stored.fileName, Aead.randomKey()) }
    }

    @Test fun fileCipherSwappedFilesFail() {
        val dir = File(tmp.root, "attachments")
        val files = FileCipher(dir)
        val a = files.write(byteArrayOf(1, 2, 3))
        val b = files.write(byteArrayOf(4, 5, 6))
        File(dir, a.fileName).writeBytes(File(dir, b.fileName).readBytes())
        expectBadTag { files.read(a.fileName, b.key) }
    }

    @Test fun fileCipherRejectsOtherNames() {
        val files = FileCipher(tmp.root)
        try {
            files.read("../vault.key", Aead.randomKey())
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun deleteAllExceptKeepsListedFiles() {
        val dir = File(tmp.root, "attachments")
        val files = FileCipher(dir)
        val keep = files.write(byteArrayOf(1))
        val stray = files.write(byteArrayOf(2))
        files.deleteAllExcept(setOf(keep.fileName))
        assertTrue(File(dir, keep.fileName).exists())
        assertFalse(File(dir, stray.fileName).exists())
        assertNotNull(files.read(keep.fileName, keep.key))
    }

    private fun expectBadTag(block: () -> Unit) {
        try {
            block()
            fail("expected AEADBadTagException")
        } catch (_: AEADBadTagException) {
        }
    }
}

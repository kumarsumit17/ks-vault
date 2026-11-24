package com.ks.vault.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import javax.crypto.AEADBadTagException

/** How the vault is locked. The secret's characters are the password, or the pattern's dots as digits 1 to 9. */
enum class SecretKind { PASSWORD, PATTERN }

/** The key file cannot be read on this phone any more (its Keystore key is gone, or the file is damaged). */
class VaultUnreadableException(cause: Throwable) : Exception(cause)

/**
 * The vault key and its two layers (specs/plan.md, "Security design"):
 *
 * ```
 * file = header || wrapper.wrap( AES-GCM(PBKDF2(secret, salt), vault key, aad = header) )
 * ```
 *
 * No secret and no hash of it is stored. A wrong secret fails the inner GCM tag check. The header
 * (kind, rounds, salt) is the inner layer's associated data, so it cannot be changed unnoticed.
 */
class VaultKeys(
    private val file: File,
    private val wrapper: KeyWrapper,
    private val iterations: Int = Kdf.ITERATIONS,
) {
    private class Header(val kind: SecretKind, val iterations: Int, val salt: ByteArray) {
        fun bytes(): ByteArray = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use {
                it.write(MAGIC)
                it.writeByte(FORMAT)
                it.writeByte(kind.ordinal)
                it.writeInt(iterations)
                it.writeByte(salt.size)
                it.write(salt)
            }
        }.toByteArray()
    }

    fun exists(): Boolean = file.isFile

    /** The kind of secret the vault was set up with, or null before setup. */
    fun kind(): SecretKind? = if (exists()) runCatching { read().first.kind }.getOrNull() else null

    /** Sets up a new vault: a new random vault key, sealed with [secret]. Returns the vault key; the caller wipes it. */
    fun create(secret: CharArray, kind: SecretKind): ByteArray {
        val vaultKey = Aead.randomKey()
        write(secret, kind, vaultKey)
        return vaultKey
    }

    /** The vault key, or null when [secret] is wrong. The caller wipes it. */
    fun unlock(secret: CharArray): ByteArray? {
        val (header, wrapped) = read()
        val inner = try {
            wrapper.unwrap(wrapped)
        } catch (e: Exception) {
            throw VaultUnreadableException(e)
        }
        val secretKey = Kdf.derive(secret, header.salt, header.iterations)
        return try {
            Aead.open(secretKey, inner, header.bytes())
        } catch (_: AEADBadTagException) {
            null
        } finally {
            secretKey.wipe()
            inner.wipe()
        }
    }

    /** Replaces the secret (and maybe its kind). The vault key stays the same, so no data is re-encrypted. */
    fun change(current: CharArray, newSecret: CharArray, newKind: SecretKind): Boolean {
        val vaultKey = unlock(current) ?: return false
        try {
            write(newSecret, newKind, vaultKey)
        } finally {
            vaultKey.wipe()
        }
        return true
    }

    /** Deletes the key file and the Keystore key: the vault can never be opened again. */
    fun erase() {
        file.delete()
        wrapper.destroy()
    }

    private fun write(secret: CharArray, kind: SecretKind, vaultKey: ByteArray) {
        val header = Header(kind, iterations, Aead.randomBytes(Kdf.SALT_BYTES))
        val headerBytes = header.bytes()
        val secretKey = Kdf.derive(secret, header.salt, header.iterations)
        val inner = try {
            Aead.seal(secretKey, vaultKey, headerBytes)
        } finally {
            secretKey.wipe()
        }
        val wrapped = wrapper.wrap(inner)
        inner.wipe()

        // Written next to the old file and renamed over it, so a crash never leaves half a key file.
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".new")
        tmp.outputStream().use { out ->
            out.write(headerBytes)
            DataOutputStream(out).apply {
                writeInt(wrapped.size)
                write(wrapped)
                flush()
            }
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) throw IOException("Could not save the key file")
    }

    private fun read(): Pair<Header, ByteArray> = try {
        DataInputStream(ByteArrayInputStream(file.readBytes())).use { input ->
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            require(magic.contentEquals(MAGIC) && input.readByte().toInt() == FORMAT) { "Not a KS Vault key file" }
            val kind = SecretKind.entries[input.readByte().toInt()]
            val rounds = input.readInt()
            val salt = ByteArray(input.readByte().toInt()).also(input::readFully)
            val wrapped = ByteArray(input.readInt()).also(input::readFully)
            Header(kind, rounds, salt) to wrapped
        }
    } catch (e: Exception) {
        throw VaultUnreadableException(e)
    }

    private companion object {
        val MAGIC = "KSVK".toByteArray()
        const val FORMAT = 1
    }
}

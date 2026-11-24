package com.ks.vault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a random 96-bit nonce. A sealed box is `nonce (12 bytes) || ciphertext || tag (16 bytes)`.
 * A wrong key or any change to the box fails with [javax.crypto.AEADBadTagException]; the tag
 * check is done in constant time by the cipher.
 */
object Aead {
    const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun randomKey(): ByteArray = randomBytes(KEY_BYTES)

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 needs a 32-byte key" }
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        aad?.let(cipher::updateAAD)
        return nonce + cipher.doFinal(plain)
    }

    fun open(key: ByteArray, box: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 needs a 32-byte key" }
        require(box.size >= NONCE_BYTES + TAG_BITS / 8) { "Too short to be a sealed box" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, box, 0, NONCE_BYTES),
        )
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(box, NONCE_BYTES, box.size - NONCE_BYTES)
    }
}

/** Fills the array with zeros, so a key or secret does not stay in memory after use. */
fun ByteArray.wipe() = fill(0)

fun CharArray.wipe() = fill('\u0000')

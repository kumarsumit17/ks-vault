package com.ks.vault.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns the pattern or password into a 256-bit key: PBKDF2-HMAC-SHA256 with a per-install random
 * salt. 600,000 rounds (OWASP 2023); the constraint is at least 310,000. Built into Android and the
 * JDK, so no library is needed (specs/plan.md, decision 1).
 */
object Kdf {
    const val ITERATIONS = 600_000
    const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun derive(secret: CharArray, salt: ByteArray, iterations: Int = ITERATIONS, bytes: Int = Aead.KEY_BYTES): ByteArray {
        require(secret.isNotEmpty()) { "The secret is empty" }
        val spec = PBEKeySpec(secret, salt, iterations, bytes * 8)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

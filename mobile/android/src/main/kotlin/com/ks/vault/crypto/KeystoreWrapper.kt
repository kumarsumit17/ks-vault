package com.ks.vault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * An AES-256-GCM key in the Android Keystore. It is created on this phone, cannot be exported,
 * and is kept in StrongBox when the phone has one. A copy of the app's files is useless without it.
 * On Android 9 and later it can be used only while the phone is unlocked, so malware running while
 * the phone is locked cannot use it (specs/plan-per-platform.md).
 */
class KeystoreWrapper(private val alias: String = ALIAS) : KeyWrapper {

    private val keyStore: KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey =
        (keyStore.getKey(alias, null) as SecretKey?) ?: create()

    private fun create(): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 28) {
                    setUnlockedDeviceRequired(true)
                    if (strongBox) setIsStrongBoxBacked(true)
                }
            }
            .build()

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                generator.init(spec(strongBox = true))
                return generator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // No StrongBox on this phone: the key stays in the Keystore's normal secure storage.
            }
        }
        generator.init(spec(strongBox = false))
        return generator.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, wrapped, 0, NONCE_BYTES))
        return cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
    }

    override fun destroy() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    companion object {
        const val ALIAS = "ks_vault_outer_v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
    }
}

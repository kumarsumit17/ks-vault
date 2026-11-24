package com.ks.vault.crypto

/**
 * The outer layer around the vault key: something that only this phone can undo. On Android it
 * is [KeystoreWrapper]; the unit tests use a plain AES key instead.
 */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray

    /** Deletes the key, which makes everything wrapped with it unreadable for ever. */
    fun destroy()
}

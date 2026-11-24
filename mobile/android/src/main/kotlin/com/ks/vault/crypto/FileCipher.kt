package com.ks.vault.crypto

import java.io.File
import java.io.IOException

/**
 * Encrypted attachment files in app-private storage. Each file has its own random key (kept in the
 * encrypted database) and its name as associated data, so files cannot be swapped. Plain bytes exist
 * only in memory.
 */
class FileCipher(private val dir: File) {

    class Stored(val fileName: String, val key: ByteArray)

    fun write(plain: ByteArray): Stored {
        dir.mkdirs()
        val name = Aead.randomBytes(16).joinToString("") { "%02x".format(it) } + ".bin"
        val key = Aead.randomKey()
        val tmp = File(dir, "$name.new")
        tmp.outputStream().use {
            it.write(Aead.seal(key, plain, name.toByteArray()))
            it.fd.sync()
        }
        if (!tmp.renameTo(File(dir, name))) throw IOException("Could not save the attachment")
        return Stored(name, key)
    }

    fun read(fileName: String, key: ByteArray): ByteArray =
        Aead.open(key, File(dir, checkName(fileName)).readBytes(), fileName.toByteArray())

    fun delete(fileName: String) {
        File(dir, checkName(fileName)).delete()
    }

    /** Deletes every file whose name is not in [keep] (left behind by a crash between file and database). */
    fun deleteAllExcept(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    private fun checkName(name: String): String {
        require(NAME.matches(name)) { "Not an attachment file name" }
        return name
    }

    private companion object {
        val NAME = Regex("^[0-9a-f]{32}\\.bin$")
    }
}

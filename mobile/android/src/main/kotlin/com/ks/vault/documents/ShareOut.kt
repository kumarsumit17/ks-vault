package com.ks.vault.documents

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.provider.OpenableColumns
import com.ks.vault.VaultApp
import com.ks.vault.auth.VaultSession
import com.ks.vault.crypto.Aead
import com.ks.vault.data.Attachment
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Sharing an attachment out of the vault, after the user confirms. The other app gets a
 * `content://com.ks.vault.share/<token>/<name>` address; reading it gets the decrypted bytes in an
 * anonymous in-memory file (Android 11+) or a pipe, so no plain copy is ever written to disk. Tokens live in memory only and stop
 * working when the vault locks (the share grace keeps it open for a few minutes, specs/sharing.md).
 */
object ShareTokens {
    private val tokens = ConcurrentHashMap<String, Long>()

    fun uriFor(attachment: Attachment): Uri {
        val token = Aead.randomBytes(16).joinToString("") { "%02x".format(it) }
        tokens[token] = attachment.id
        val safeName = attachment.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "file" }
        return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(token).appendPath(safeName).build()
    }

    fun attachmentId(uri: Uri): Long? = uri.pathSegments.firstOrNull()?.let { tokens[it] }

    fun clear() = tokens.clear()

    const val AUTHORITY = "com.ks.vault.share"
}

class VaultShareProvider : ContentProvider() {
    override fun onCreate() = true

    private fun lookup(uri: Uri): Pair<VaultSession.State.Unlocked, Attachment>? {
        val session = (context?.applicationContext as? VaultApp)?.session ?: return null
        val state = session.state.value as? VaultSession.State.Unlocked ?: return null
        val id = ShareTokens.attachmentId(uri) ?: return null
        val attachment = runBlocking { state.vault.attachment(id) } ?: return null
        return state to attachment
    }

    override fun getType(uri: Uri): String? = lookup(uri)?.second?.mime

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val (_, a) = lookup(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply {
            addRow(cols.map { if (it == OpenableColumns.SIZE) a.size else if (it == OpenableColumns.DISPLAY_NAME) a.name else null })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        val (state, a) = lookup(uri) ?: throw FileNotFoundException("The vault is locked or the share has ended")
        val bytes = runBlocking { state.vault.read(a) }
        if (Build.VERSION.SDK_INT >= 30) {
            // An anonymous file in memory (memfd): seekable, so PDF viewers and renderers can read it,
            // and never written to storage.
            try {
                val fd = Os.memfd_create("vault-share", 0)
                try {
                    var off = 0
                    while (off < bytes.size) off += Os.write(fd, bytes, off, bytes.size - off)
                    Os.lseek(fd, 0, OsConstants.SEEK_SET)
                    return ParcelFileDescriptor.dup(fd)
                } finally {
                    Os.close(fd)
                    bytes.fill(0)
                }
            } catch (e: ErrnoException) {
                throw FileNotFoundException("Could not share: ${e.errno}")
            }
        }
        // Android 10 and older: a pipe (no seeking, but nothing on disk).
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        thread(name = "vault-share") {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
            } catch (_: Exception) {
                runCatching { write.closeWithError("Stopped") }
            } finally {
                bytes.fill(0)
            }
        }
        return read
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

/** Opens the share sheet for one attachment (after the user confirmed). */
fun shareAttachment(context: Context, session: VaultSession, attachment: Attachment, title: String) {
    val uri = ShareTokens.uriFor(attachment)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = attachment.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    session.expectExternal()
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Opens the share sheet with an item's text (after the user confirmed). */
fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

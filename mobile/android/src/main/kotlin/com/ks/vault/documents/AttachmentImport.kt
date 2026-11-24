package com.ks.vault.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.ks.vault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Copies a photo or PDF the user picked into the vault. The bytes are read into memory and
 * encrypted at once; nothing is written to the cache. A PDF's pages are drawn into JPEG images here,
 * from the picker's own file, because [PdfRenderer] needs a real file and the vault never makes a
 * plain copy of one.
 */
object AttachmentImport {
    const val MAX_MB = 20
    private const val MAX_BYTES = MAX_MB * 1024 * 1024
    private const val MAX_PAGES = 50
    private const val PAGE_WIDTH = 1600

    enum class Result { Ok, TooLarge, Unsupported, Failed }

    suspend fun import(context: Context, vault: VaultRepository, itemId: Long, uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: return@withContext Result.Unsupported
            if (mime != "application/pdf" && !mime.startsWith("image/")) return@withContext Result.Unsupported
            var name = "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            if (size > MAX_BYTES) return@withContext Result.TooLarge
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return@withContext Result.TooLarge
                }
                out.toByteArray()
            } ?: return@withContext Result.Failed
            val pages = if (mime == "application/pdf") renderPdf(context, uri) ?: return@withContext Result.Unsupported else emptyList()
            vault.addAttachment(itemId, name, mime, bytes, pages)
            bytes.fill(0)
            Result.Ok
        } catch (_: SecurityException) {
            Result.Failed
        } catch (_: java.io.IOException) {
            Result.Failed
        }
    }

    /** JPEG images of the PDF's pages (at most [MAX_PAGES]), or null when it cannot be read. */
    private fun renderPdf(context: Context, uri: Uri): List<ByteArray>? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { pdf ->
                (0 until minOf(pdf.pageCount, MAX_PAGES)).map { i ->
                    pdf.openPage(i).use { page ->
                        val scale = PAGE_WIDTH.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        bitmap.recycle()
                        out.toByteArray()
                    }
                }
            }
        }
    } catch (_: Exception) {
        // Damaged or password-protected PDF.
        null
    }
}

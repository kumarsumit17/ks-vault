package com.ks.vault.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.data.Attachment
import com.ks.vault.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes an image from memory, scaled down so its longer side is about [maxSide] pixels. */
fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** A decrypted image of [attachment] (a PDF shows its first page), recycled when it leaves the screen. */
@Composable
fun rememberDecrypted(vault: VaultRepository, attachment: Attachment, maxSide: Int): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, attachment.id, maxSide) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = if (attachment.isPdf) vault.pages(attachment).firstOrNull() else attachment
                source?.let { a -> vault.read(a).let { bytes -> decodeScaled(bytes, maxSide).also { bytes.fill(0) } } }
            }.getOrNull()
        }
    }
    DisposableEffect(bitmap) {
        val b = bitmap
        onDispose { b?.recycle() }
    }
    return bitmap
}

/** The attachments of an item as a row of thumbnails. */
@Composable
fun AttachmentStrip(vault: VaultRepository, attachments: List<Attachment>, onOpen: (Attachment) -> Unit) {
    if (attachments.isEmpty()) {
        Text(stringResource(R.string.attach_none), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        attachments.forEach { a ->
            Column(Modifier.width(112.dp).clickable { onOpen(a) }.testTag("attachment")) {
                Box(
                    Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = rememberDecrypted(vault, a, 320)
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp))
                    }
                    if (a.isPdf) {
                        Text(
                            "PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                        )
                    }
                }
                Text(a.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

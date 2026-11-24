package com.ks.vault.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import com.ks.vault.data.Attachment
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import kotlinx.coroutines.launch

/** A photo full screen (pinch to zoom), or a PDF's pages one under the other. Decrypted in memory only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewer(vault: VaultRepository, nav: Navigator, attachmentId: Long) {
    val scope = rememberCoroutineScope()
    val attachment by produceState<Attachment?>(null, attachmentId) { value = vault.attachment(attachmentId) }
    val pages by produceState<List<Attachment>?>(null, attachment) {
        val a = attachment ?: return@produceState
        value = if (a.isPdf) vault.pages(a) else listOf(a)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(attachment?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = attachment != null) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.attach_delete))
                    }
                },
            )
        },
    ) { padding ->
        val list = pages
        if (list == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.size == 1 && attachment?.isPdf == false) {
            ZoomableImage(vault, list[0], Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("pages")) {
                items(list, key = { it.id }) { page ->
                    PageImage(vault, page)
                }
            }
        }
    }

    val a = attachment
    if (confirmDelete && a != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.common_confirm_remove_title, a.name)) },
            text = { Text(stringResource(R.string.common_confirm_cannot_undo)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vault.deleteAttachment(a)
                        nav.back()
                    }
                }) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun PageImage(vault: VaultRepository, page: Attachment) {
    val bmp = rememberDecrypted(vault, page, 1600)
    if (bmp == null) {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Image(bmp.asImageBitmap(), contentDescription = page.name, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}

@Composable
private fun ZoomableImage(vault: VaultRepository, attachment: Attachment, modifier: Modifier) {
    val bmp = rememberDecrypted(vault, attachment, 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bmp.asImageBitmap(),
                contentDescription = attachment.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}

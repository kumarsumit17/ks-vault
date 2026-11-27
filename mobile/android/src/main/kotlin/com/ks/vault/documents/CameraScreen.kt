package com.ks.vault.documents

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ks.vault.R
import com.ks.vault.data.VaultRepository
import com.ks.vault.ui.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Takes a photo inside the app with CameraX and saves it straight into the vault. The picture stays
 * in memory (ImageCapture's in-memory callback) and is encrypted at once: no camera app and no file
 * ever holds a plain copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(vault: VaultRepository, nav: Navigator, itemId: Long) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var ratio by remember { mutableFloatStateOf(3f / 4f) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!granted) {
                Text(
                    stringResource(if (asked) R.string.camera_denied else R.string.camera_asking),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { view -> bindCamera(ctx, lifecycleOwner, view, capture) { r -> ratio = r } }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                )
                if (busy) CircularProgressIndicator()
            }
            if (failed) Text(stringResource(R.string.camera_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            Button(
                onClick = {
                    busy = true
                    failed = false
                    capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            scope.launch {
                                val jpeg = withContext(Dispatchers.Default) { image.use { uprightJpeg(it) } }
                                val name = "Photo " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")) + ".jpg"
                                vault.addAttachment(itemId, name, "image/jpeg", jpeg)
                                jpeg.fill(0)
                                busy = false
                                nav.back()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                            failed = true
                        }
                    })
                },
                enabled = !busy,
                modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("shutter"),
            ) { Text(stringResource(R.string.camera_take)) }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
}

/** Binds the preview (drawn on [view]) and [capture] to the back camera. [onRatio] gets width / height. */
private fun bindCamera(
    context: android.content.Context,
    owner: androidx.lifecycle.LifecycleOwner,
    view: SurfaceView,
    capture: ImageCapture,
    onRatio: (Float) -> Unit,
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(ContextCompat.getMainExecutor(context)) { request ->
            val size = request.resolution
            // The camera's frames are landscape; on a phone held upright the preview is portrait.
            onRatio(minOf(size.width, size.height).toFloat() / maxOf(size.width, size.height))
            val holder = view.holder
            holder.setFixedSize(size.width, size.height)
            fun provide() = request.provideSurface(holder.surface, ContextCompat.getMainExecutor(context)) {}
            if (holder.surface?.isValid == true) {
                provide()
            } else {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) = provide()
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
            }
        }
        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
    }, ContextCompat.getMainExecutor(context))
}

/** The captured JPEG turned upright (CameraX reports the rotation instead of applying it). */
private fun uprightJpeg(image: ImageProxy): ByteArray {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also(buffer::get)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bytes
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    source.recycle()
    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
    rotated.recycle()
    bytes.fill(0)
    return out.toByteArray()
}

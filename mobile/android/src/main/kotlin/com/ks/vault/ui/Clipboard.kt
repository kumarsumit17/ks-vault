package com.ks.vault.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import com.ks.vault.R

/**
 * Copies one field. The copy is marked sensitive, so Android does not show it in its preview,
 * and is cleared after [CLEAR_AFTER_MS] unless something else was copied since.
 */
object Clipboard {
    const val CLEAR_AFTER_MS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun copySensitive(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("KS Vault", text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        val mine = ++generation
        val copiedAt = android.os.SystemClock.elapsedRealtime()
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            // Our own copy may be reported just after it is made; a later change means the user
            // copied something else, which must not be cleared.
            if (android.os.SystemClock.elapsedRealtime() - copiedAt > 1_000 && generation == mine) generation++
        }
        cm.addPrimaryClipChangedListener(listener)
        cm.setPrimaryClip(clip)
        handler.postDelayed({
            cm.removePrimaryClipChangedListener(listener)
            if (generation == mine) {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, CLEAR_AFTER_MS)
        // Android 13 and later show their own confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.copy_done, Toast.LENGTH_SHORT).show()
    }
}

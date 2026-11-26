package com.ks.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ks.vault.ui.NoLearningKeyboard
import com.ks.vault.ui.VaultRoot
import com.ks.vault.ui.VaultTheme

/** The one activity. AppCompat only for the per-app language; everything on screen is Compose. */
class MainActivity : AppCompatActivity() {

    private val app get() = application as VaultApp

    override fun onCreate(savedInstanceState: Bundle?) {
        // No screenshots, no screen recording, and a blank thumbnail in recents. Compose dialogs and
        // menus inherit it from this window.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        WindowHardening.apply(window.decorView)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        receiveShare(intent)
        setContent {
            VaultTheme {
                NoLearningKeyboard {
                    VaultRoot(app.session, app.settings)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    /** "Save to KS Vault" from another app: keep the files' addresses until the vault is unlocked. */
    private fun receiveShare(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        app.session.receiveShared(uris)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        app.session.touch()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        app.session.touch()
        return super.dispatchKeyEvent(event)
    }
}

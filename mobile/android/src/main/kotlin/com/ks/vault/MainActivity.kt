package com.ks.vault

import android.os.Bundle
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
        setContent {
            VaultTheme {
                NoLearningKeyboard {
                    VaultRoot(app.session, app.settings)
                }
            }
        }
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

package com.ks.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ks.vault.auth.VaultSession
import com.ks.vault.auth.VaultSettings

class VaultApp : Application() {
    // Made on first use, not at process start: before the phone's first unlock after a restart,
    // Android's credential-encrypted storage (the settings) cannot be read yet.
    val settings: VaultSettings by lazy { VaultSettings(this) }
    val session: VaultSession by lazy { VaultSession(this, settings) }

    override fun onCreate() {
        super.onCreate()
        // Lock when the whole app leaves the screen (home, another app, screen off).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = session.onForeground()
            override fun onStop(owner: LifecycleOwner) = session.onBackground()
        })
    }
}

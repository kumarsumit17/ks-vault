package com.ks.vault

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ks.vault.auth.VaultSession
import com.ks.vault.auth.VaultSettings

class VaultApp : Application() {
    lateinit var settings: VaultSettings
        private set
    lateinit var session: VaultSession
        private set

    override fun onCreate() {
        super.onCreate()
        settings = VaultSettings(this)
        session = VaultSession(this, settings)
        // Lock when the whole app leaves the screen (home, another app, screen off).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = session.onForeground()
            override fun onStop(owner: LifecycleOwner) = session.onBackground()
        })
    }
}

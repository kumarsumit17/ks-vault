package com.ks.vault.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The app's settings that are not secret (auto-lock time). The language is kept by Android/AppCompat. */
class VaultSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _autoLock = MutableStateFlow(prefs.getInt(KEY_AUTO_LOCK, DEFAULT_AUTO_LOCK).takeIf { it in AUTO_LOCK_CHOICES } ?: DEFAULT_AUTO_LOCK)
    val autoLock: StateFlow<Int> = _autoLock

    val autoLockMinutes: Int get() = _autoLock.value

    fun setAutoLockMinutes(minutes: Int) {
        require(minutes in AUTO_LOCK_CHOICES)
        prefs.edit().putInt(KEY_AUTO_LOCK, minutes).apply()
        _autoLock.value = minutes
    }

    private val _reminders = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, false))
    /** ID card expiry reminders; off until turned on. */
    val reminders: StateFlow<Boolean> = _reminders

    fun setReminders(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, on).apply()
        _reminders.value = on
    }

    companion object {
        private const val KEY_REMINDERS = "expiryReminders"
        val AUTO_LOCK_CHOICES = listOf(1, 2, 5, 10)
        const val DEFAULT_AUTO_LOCK = 2
        private const val KEY_AUTO_LOCK = "autoLockMinutes"
    }
}

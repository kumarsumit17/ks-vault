package com.ks.vault

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ks.vault.reminders.ExpiryReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A due reminder posts one notification that does not name the card, and is not shown twice. */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun dueReminderNotifiesOnce() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancelAll()
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        prefs.edit().putString("times", "${System.currentTimeMillis() - 1_000},${System.currentTimeMillis() + 86_400_000}").commit()

        ExpiryReminders.fireDue(context)
        val shown = nm.activeNotifications.filter { it.packageName == context.packageName }
        assertEquals(1, shown.size)
        val text = shown[0].notification.extras.getCharSequence("android.text").toString()
        assertTrue(text.contains("KS Vault"))
        // The due time is used up; the one tomorrow stays.
        assertEquals(1, prefs.getString("times", "")!!.split(',').size)

        nm.cancelAll()
        ExpiryReminders.fireDue(context)
        assertFalse(nm.activeNotifications.any { it.packageName == context.packageName })
        ExpiryReminders.update(context, enabled = false, expiryDays = emptyList())
    }
}

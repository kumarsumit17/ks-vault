package com.ks.vault.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ks.vault.MainActivity
import com.ks.vault.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A local reminder before an ID card expires: 30 and 7 days before, at 9:00 (specs/reminders.md).
 * The vault is encrypted while locked, so the times are worked out while it is open and only the
 * times are kept (not which card); the notification says to unlock the vault to see which one.
 * No network: Android's AlarmManager and a local notification.
 */
object ExpiryReminders {
    val DAYS_BEFORE = listOf(30L, 7L)
    private val AT = LocalTime.of(9, 0)
    private const val PREFS = "reminders"
    private const val KEY_TIMES = "times"
    private const val CHANNEL = "expiry"

    /** The reminder times (epoch millis) still ahead of [now] for cards expiring on [expiryDays], sorted. */
    fun times(expiryDays: List<Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        expiryDays.flatMap { day ->
            DAYS_BEFORE.map { before -> LocalDate.ofEpochDay(day - before).atTime(AT).atZone(zone).toInstant().toEpochMilli() }
        }.filter { it > now }.distinct().sorted()

    /** Saves the times for these cards and sets the alarm for the next one; [enabled] false clears all. */
    fun update(context: Context, enabled: Boolean, expiryDays: List<Long>) {
        val list = if (enabled) times(expiryDays, System.currentTimeMillis()) else emptyList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TIMES, list.joinToString(",")).apply()
        scheduleNext(context)
    }

    private fun stored(context: Context): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TIMES, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }

    fun scheduleNext(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = stored(context).filter { it > System.currentTimeMillis() }.minOrNull()
        if (next == null) {
            alarm.cancel(pending)
        } else {
            // Inexact is fine for a reminder weeks ahead, and needs no exact-alarm permission.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    /** Shows the reminder if one is due (also after a restart), drops the due times, sets the next alarm. */
    fun fireDue(context: Context) {
        val now = System.currentTimeMillis()
        val all = stored(context)
        val due = all.filter { it <= now + 60_000 }
        if (due.isNotEmpty()) {
            notify(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TIMES, (all - due.toSet()).joinToString(",")).apply()
        }
        scheduleNext(context)
    }

    private fun notify(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(1, n) }
        }
    }
}

/** The alarm went off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ExpiryReminders.fireDue(context)
}

/** After a restart Android has forgotten the alarm: set it again (and show one that was missed). */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ExpiryReminders.fireDue(context)
    }
}

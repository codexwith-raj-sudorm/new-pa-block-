package com.jarvis.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Fires when a reminder alarm goes off: removes it from the store and notifies. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("rid", -1)
        val text = intent.getStringExtra("text").orEmpty().ifBlank { "Reminder!" }
        if (id != -1) runCatching { Store(context).removeReminder(id) }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                nm.createNotificationChannel(
                    NotificationChannel("jarvis_reminders", "Jarvis reminders", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
        val open = PendingIntent.getActivity(
            context, 1000 + id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, "jarvis_reminders")
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(5000 + id, notif) }
    }
}

/** Shared alarm arming: the VM schedules, BootReceiver re-arms after reboot. */
fun armReminderAlarm(ctx: Context, id: Int, at: Long, text: String) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        ctx, id,
        Intent(ctx, ReminderReceiver::class.java).putExtra("rid", id).putExtra("text", text),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } else {
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }
}

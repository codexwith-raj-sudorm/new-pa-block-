package com.jarvis.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

const val BRIEFING_REQ = 7001

/** Daily 8 AM proactive briefing: greeting + battery + weather, no chat needed. */
class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                val text = briefingText(
                    daypart(java.time.LocalTime.now().hour),
                    batteryPct(app),
                    runCatching { fetchBriefingWeather() }.getOrDefault("")
                )
                notifyBriefing(app, text)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Arm (or cancel) the repeating 8 AM briefing alarm. */
fun armDailyBriefing(ctx: Context, on: Boolean) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        ctx, BRIEFING_REQ, Intent(ctx, BriefingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    if (!on) {
        runCatching { am.cancel(pi) }
        return
    }
    val now = java.time.LocalDateTime.now()
    var fire = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
    if (!fire.isAfter(now)) fire = fire.plusDays(1)
    val at = fire.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    runCatching { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pi) }
}

private fun batteryPct(ctx: Context): Int {
    return try {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (l >= 0 && s > 0) l * 100 / s else -1
    } catch (_: Exception) {
        -1
    }
}

private fun fetchBriefingWeather(): String {
    val req = Request.Builder()
        .url("https://wttr.in/?format=%C+%t")
        .header("User-Agent", "curl/8.0").build()
    OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        .newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            return resp.body?.string()?.trim().orEmpty().take(60)
        }
}

private fun notifyBriefing(ctx: Context, text: String) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel("jarvis_briefing", "Jarvis briefing", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
    val open = PendingIntent.getActivity(
        ctx, BRIEFING_REQ,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notif = NotificationCompat.Builder(ctx, "jarvis_briefing")
        .setSmallIcon(R.drawable.ic_stat_jarvis)
        .setContentTitle("Jarvis briefing")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    runCatching { nm.notify(BRIEFING_REQ, notif) }
}

/** "Good morning! Battery 87%. Outside: Sunny +31C." Pure, tested. */
fun briefingText(greet: String, battPct: Int, weather: String): String {
    val parts = mutableListOf("$greet!")
    if (battPct >= 0) parts.add("Battery $battPct%.")
    if (weather.isNotBlank()) parts.add("Outside: $weather.")
    return parts.joinToString(" ")
}

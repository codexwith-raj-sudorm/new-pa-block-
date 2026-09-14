package com.jarvis.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Fires when a scheduled message is due: sends it (or opens the chat) + notifies. */
class SchedMsgReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val id = intent.getIntExtra("sid", -1)
                val app = intent.getStringExtra("app").orEmpty()
                val label = intent.getStringExtra("label").orEmpty()
                val number = intent.getStringExtra("number").orEmpty()
                val body = intent.getStringExtra("body").orEmpty()
                if (id != -1) runCatching { Store(context).removeSchedMsg(id) }
                if (number.isBlank() || body.isBlank()) return@Thread
                if (app == "wa") {
                    if (!openWhatsAppNow(context, number, body)) {
                        notifyMsg(context, id, "Couldn't open WhatsApp for $label.", null)
                        return@Thread
                    }
                    var tapped = false
                    if (isAccessEnabled(context)) {
                        Thread.sleep(3000)
                        tapped = runCatching { AccessBridge.tapDesc("Send") }.getOrNull() == true
                    }
                    if (tapped) notifyMsg(context, id, "Sent to $label on WhatsApp.", null)
                    else {
                        val open = PendingIntent.getActivity(
                            context, 9000 + (id % 1000),
                            whatsAppIntent(number, body),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        notifyMsg(context, id, "Time to send to $label — tap to open the chat.", open)
                    }
                } else {
                    if (sendSmsNow(context, number, body)) {
                        notifyMsg(context, id, "Sent to $label: “${body.take(80)}”", null)
                    } else {
                        notifyMsg(context, id, "Couldn't send to $label — SMS permission?", null)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

private fun notifyMsg(ctx: Context, id: Int, text: String, open: PendingIntent?) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel("jarvis_sends", "Jarvis sent messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
    val b = NotificationCompat.Builder(ctx, "jarvis_sends")
        .setSmallIcon(R.drawable.ic_stat_jarvis)
        .setContentTitle("Jarvis")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
    if (open != null) b.setContentIntent(open)
    runCatching { nm.notify(7000 + (id % 1000), b.build()) }
}

/** Same exact-alarm pattern as reminders; ids live above 100000 to never collide. */
fun armSchedMsgAlarm(
    ctx: Context, id: Int, at: Long,
    app: String, label: String, number: String, body: String
) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        ctx, id,
        Intent(ctx, SchedMsgReceiver::class.java).putExtra("sid", id).putExtra("app", app)
            .putExtra("label", label).putExtra("number", number).putExtra("body", body),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } else {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

/** Direct SMS send shared by the send-now path and the scheduled-message receiver. */
fun sendSmsNow(ctx: Context, number: String, body: String): Boolean {
    if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS)
        != PackageManager.PERMISSION_GRANTED
    ) return false
    return try {
        @Suppress("DEPRECATION")
        val sm = android.telephony.SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size <= 1) sm.sendTextMessage(number, null, body, null, null)
        else sm.sendMultipartTextMessage(number, null, parts, null, null)
        true
    } catch (_: Exception) {
        false
    }
}

fun whatsAppIntent(number: String, body: String): Intent {
    val digits = waDigits(number, java.util.Locale.getDefault().country ?: "")
    val uri = Uri.parse("https://wa.me/$digits?text=" + java.net.URLEncoder.encode(body, "UTF-8"))
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun openWhatsAppNow(ctx: Context, number: String, body: String): Boolean {
    return try {
        ctx.startActivity(whatsAppIntent(number, body))
        true
    } catch (_: Exception) {
        false
    }
}

fun cancelSchedMsgAlarm(ctx: Context, id: Int) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, SchedMsgReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(pi)
}

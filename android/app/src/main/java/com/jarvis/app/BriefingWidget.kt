package com.jarvis.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews

/** Home-screen briefing widget: time, battery, next reminder. Tap opens Jarvis. */
class BriefingWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.jarvis.app.BRIEFING_REFRESH"
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val comp = android.content.ComponentName(context, BriefingWidget::class.java)
            for (id in mgr.getAppWidgetIds(comp)) updateOne(context, mgr, id)
        }
    }
}

private fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
    val v = RemoteViews(ctx.packageName, R.layout.widget_briefing)
    val now = java.time.LocalDateTime.now()
    v.setTextViewText(R.id.bw_time, now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
    v.setTextViewText(R.id.bw_date, now.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")) + widgetMasterLine(ctx))
    v.setTextViewText(R.id.bw_batt, "Battery " + battPct(ctx) + "%  ↻")
    val at = System.currentTimeMillis()
    val next = Store(ctx).loadReminders().filter { it.at > at }.minByOrNull { it.at }
    v.setTextViewText(R.id.bw_next, widgetReminderLine(next, at))
    val open = PendingIntent.getActivity(
        ctx, 8001, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    v.setOnClickPendingIntent(R.id.bw_root, open)
    val refresh = PendingIntent.getBroadcast(
        ctx, 8002, Intent(ctx, BriefingWidget::class.java).setAction(BriefingWidget.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    v.setOnClickPendingIntent(R.id.bw_batt, refresh)
    runCatching { mgr.updateAppWidget(id, v) }
}

/** " · Master <first>" suffix for the widget when a master key is installed. */
private fun widgetMasterLine(ctx: Context): String {
    return try {
        val s = Store(ctx)
        if (s.masterKey.isNotBlank() && s.masterName.isNotBlank()) " · Master " + firstName(s.masterName) else ""
    } catch (_: Exception) {
        ""
    }
}

private fun battPct(ctx: Context): Int {
    return try {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (l >= 0 && s > 0) l * 100 / s else -1
    } catch (_: Exception) {
        -1
    }
}

/** Widget next-reminder line. Pure, tested. */
fun widgetReminderLine(next: ReminderItem?, now: Long): String {
    if (next == null) return "No reminders"
    return "Next: " + next.text.take(30) + " " + dueText(next.at, now)
}

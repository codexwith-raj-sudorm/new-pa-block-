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
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
    }
}

private fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
    val v = RemoteViews(ctx.packageName, R.layout.widget_briefing)
    val now = java.time.LocalDateTime.now()
    v.setTextViewText(R.id.bw_time, now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
    v.setTextViewText(R.id.bw_date, now.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")))
    v.setTextViewText(R.id.bw_batt, "Battery " + battPct(ctx) + "%")
    val at = System.currentTimeMillis()
    val next = Store(ctx).loadReminders().filter { it.at > at }.minByOrNull { it.at }
    v.setTextViewText(R.id.bw_next, widgetReminderLine(next, at))
    val open = PendingIntent.getActivity(
        ctx, 8001, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    v.setOnClickPendingIntent(R.id.bw_root, open)
    runCatching { mgr.updateAppWidget(id, v) }
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

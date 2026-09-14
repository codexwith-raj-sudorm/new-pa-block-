package com.jarvis.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Tap action the widget's PendingIntent fires at MainActivity. */
const val ACTION_WIDGET_TAP = "com.jarvis.app.WIDGET_TAP"

/** Process-wide speech flag: VM replies and the service "Yes sir?" both report here. */
object SpeechState {
    @Volatile var speaking: Boolean = false
}

enum class TapAction { INTERRUPT, WAKE_ON, WAKE_OFF }

/** Pure tap decision (unit-tested): interrupting speech always wins over the wake toggle. */
fun widgetTapAction(speaking: Boolean, wakeOn: Boolean): TapAction =
    if (speaking) TapAction.INTERRUPT else if (wakeOn) TapAction.WAKE_OFF else TapAction.WAKE_ON

/** Mini arc-reactor home-screen widget: tap to arm wake mode, tap while talking to interrupt. */
class ReactorWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        refreshReactorWidgets(context)
    }
}

/** Rebinds every reactor widget (tap target + bright/dim state). Safe to call anywhere. */
fun refreshReactorWidgets(context: Context) {
    try {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ReactorWidget::class.java))
        if (ids.isEmpty()) return
        val tap = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).setAction(ACTION_WIDGET_TAP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_reactor)
            views.setOnClickPendingIntent(R.id.reactor_tap, tap)
            views.setInt(R.id.reactor_tap, "setAlpha", if (WakeService.isRunning) 255 else 110)
            mgr.updateAppWidget(id, views)
        }
    } catch (_: Exception) { }
}

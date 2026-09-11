package com.jarvis.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jarvis.app.MainActivity
import com.jarvis.app.R

class StarkWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_WAKE = "com.jarvis.app.STARK_TOGGLE_WAKE"
        const val ACTION_STARK_WAKE = "com.jarvis.app.STARK_WAKE_SET"
        const val EXTRA_WAKE_ON = "com.jarvis.app.EXTRA_WAKE_ON"
        private const val PREFS = "stark_widget"
        private const val KEY_AWAKE = "awake"

        private fun isAwake(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AWAKE, false)

        private fun setAwake(ctx: Context, awake: Boolean) =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AWAKE, awake).apply()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.stark_widget_layout)

        if (isAwake(context)) {
            views.setTextViewText(R.id.widget_status_text, "JARVIS: ACTIVE")
        } else {
            views.setTextViewText(R.id.widget_status_text, "STANDBY")
        }

        val intent = Intent(context, StarkWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_WAKE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_reactor_icon, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WAKE) {
            val nowAwake = !isAwake(context)
            setAwake(context, nowAwake)

            // Drive real wake-word listening via MainActivity (foreground-safe).
            try {
                val go = Intent(context, MainActivity::class.java)
                    .setAction(ACTION_STARK_WAKE)
                    .putExtra(EXTRA_WAKE_ON, nowAwake)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                context.startActivity(go)
            } catch (_: Exception) {
            }

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, StarkWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

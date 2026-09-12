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
import com.jarvis.app.Store
import com.jarvis.app.WakeService

/** Widget status label for the Stark toggle (pure, tested). */
fun starkWidgetLabel(awake: Boolean): String = if (awake) "JARVIS: ACTIVE" else "STANDBY"

class StarkWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_WAKE = "com.jarvis.app.STARK_TOGGLE_WAKE"
        const val ACTION_STARK_WAKE = "com.jarvis.app.STARK_WAKE_SET"
        const val EXTRA_WAKE_ON = "com.jarvis.app.EXTRA_WAKE_ON"
        /** Live truth: the wake service is up, or wake mode is armed. */
        fun isAwake(ctx: Context): Boolean = try {
            WakeService.isRunning || Store(ctx.applicationContext).wakeEnabled
        } catch (_: Exception) {
            false
        }

        /** Rebind every Stark widget from live truth. Safe to call anywhere. */
        fun refreshAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, StarkWidgetProvider::class.java))
                if (ids.isEmpty()) return
                for (id in ids) StarkWidgetProvider().updateAppWidget(ctx, mgr, id)
            } catch (_: Exception) {
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.stark_widget_layout)

        views.setTextViewText(R.id.widget_status_text, starkWidgetLabel(isAwake(context)))

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

            refreshAll(context)
        }
    }
}

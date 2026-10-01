package com.jarvis.app.backend.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jarvis.app.frontend.widgets.StarkWidgetProvider
import com.jarvis.app.backend.brain.Store
import com.jarvis.app.frontend.widgets.refreshReactorWidgets

/** After reboot: re-arm the wake service (if it was on) + pending reminder alarms. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        val inWin = !store.standbyWinOn ||
            inStandbyWindow(nowMinuteOfDay(), store.standbyStart, store.standbyEnd)
        if (store.wakeEnabled && inWin) {
            runCatching {
                val i = Intent(context, WakeService::class.java).setAction(WakeService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            }
        }
        val now = System.currentTimeMillis()
        val pending = store.loadReminders().filter { it.at > now }
        store.saveReminders(pending)
        for (r in pending) runCatching { armReminderAlarm(context, r.id, r.at, r.text) }
        val msgStore = Store(context)
        val pendingMsgs = msgStore.loadSchedMsgs().filter { it.at > System.currentTimeMillis() }
        msgStore.saveSchedMsgs(pendingMsgs)
        for (m in pendingMsgs) runCatching {
            armSchedMsgAlarm(context, m.id, m.at, m.app, m.label, m.number, m.body)
        }
        if (store.dailyBriefing) runCatching { armDailyBriefing(context, true) }
        runCatching { armStandbyWatchdog(context, store.wakeEnabled) }
        runCatching {
            armStandbyWindow(
                context, store.wakeEnabled && store.standbyWinOn,
                store.standbyStart, store.standbyEnd
            )
        }
        runCatching { StarkWidgetProvider.refreshAll(context) }
        runCatching { refreshReactorWidgets(context) }
    }
}

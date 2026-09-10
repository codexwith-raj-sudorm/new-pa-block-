package com.jarvis.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** After reboot: re-arm the wake service (if it was on) + pending reminder alarms. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        if (store.wakeEnabled) {
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
    }
}

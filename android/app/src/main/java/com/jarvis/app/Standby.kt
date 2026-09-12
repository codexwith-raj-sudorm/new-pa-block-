package com.jarvis.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 24x7 standby: heartbeat watchdog + OEM autostart routing. */

const val STANDBY_HEARTBEAT_MS = 15 * 60 * 1000L
const val STANDBY_RETRY_MS = 5 * 60 * 1000L
private const val WATCHDOG_REQ = 6101

/** Pure: revive the service only when armed but dead. Tested. */
fun shouldRevive(wakeEnabled: Boolean, serviceRunning: Boolean): Boolean =
    wakeEnabled && !serviceRunning

/** Pure: storm guard — hot error loops back off 60s. Tested. */
fun standbyBackoffMs(errorsInLastMinute: Int): Long =
    if (errorsInLastMinute > 20) 60_000L else 0L

const val STANDBY_TOAST_GAP_MS = 30 * 60 * 1000L

/** Pure: throttle standby-retry toasts to one per 30 min. Tested. */
fun shouldStandbyToast(nowMs: Long, lastToastMs: Long): Boolean =
    nowMs - lastToastMs >= STANDBY_TOAST_GAP_MS

/** Pure: OEM autostart screen target (package, activity) or null. Tested. */
fun autoStartTarget(manufacturer: String): Pair<String, String>? = when (manufacturer.lowercase()) {
    "xiaomi", "redmi", "poco" -> "com.miui.securitycenter" to
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    "oppo", "realme", "oneplus" -> "com.coloros.safecenter" to
        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
    "vivo", "iqoo" -> "com.vivo.permissionmanager" to
        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
    "huawei" -> "com.huawei.systemmanager" to
        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
    "honor" -> "com.hihonor.systemmanager" to
        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
    else -> null
}

/** Open the vendor autostart screen (falls back to app details). False if nothing opened. */
fun openAutoStartSettings(ctx: Context): Boolean {
    val t = try {
        autoStartTarget(Build.MANUFACTURER ?: "")
    } catch (_: Exception) {
        null
    }
    if (t != null) {
        try {
            ctx.startActivity(
                Intent().setClassName(t.first, t.second)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (_: Exception) {
        }
    }
    return try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Heartbeat: revive a dead service or resume a paused loop. Self-perpetuating
 * while wake stays armed (re-arms every fire); cancelled on user stop.
 */
class StandbyWatchdog : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val store = Store(context.applicationContext)
            if (!store.wakeEnabled) return
            if (!WakeService.isRunning) {
                val i = Intent(context, WakeService::class.java).setAction(WakeService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            } else {
                context.startService(
                    Intent(context, WakeService::class.java).setAction(WakeService.ACTION_RESUME)
                )
            }
            armStandbyWatchdog(context, true)
        } catch (_: Exception) {
        }
    }
}

/** Arm (or cancel) the heartbeat. Inexact + idle-allowed: Doze-friendly. */
fun armStandbyWatchdog(ctx: Context, on: Boolean) {
    try {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, WATCHDOG_REQ, Intent(ctx, StandbyWatchdog::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!on) {
            runCatching { am.cancel(pi) }
            return
        }
        armStandbyAt(am, pi, System.currentTimeMillis() + STANDBY_HEARTBEAT_MS)
    } catch (_: Exception) {
    }
}

/** One-shot recheck (mic-busy / error backoff path). */
fun armStandbyRetry(ctx: Context) {
    try {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, WATCHDOG_REQ, Intent(ctx, StandbyWatchdog::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        armStandbyAt(am, pi, System.currentTimeMillis() + STANDBY_RETRY_MS)
    } catch (_: Exception) {
    }
}

private fun armStandbyAt(am: AlarmManager, pi: PendingIntent, at: Long) {
    try {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } catch (_: Exception) {
        runCatching { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
    }
}

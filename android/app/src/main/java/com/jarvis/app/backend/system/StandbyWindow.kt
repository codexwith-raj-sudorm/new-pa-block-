package com.jarvis.app.backend.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jarvis.app.backend.brain.Store
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** §6 time-boxed standby: the listener lives only inside the user's window. */

private const val WINDOW_REQ = 5987

/**
 * Pure: is [nowMin] (minutes since midnight) inside [startMin, endMin)?
 * Overnight windows wrap (22:00–06:00); start == end means always. Tested.
 */
fun inStandbyWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean {
    val s = startMin.mod(1440)
    val e = endMin.mod(1440)
    if (s == e) return true
    val n = nowMin.mod(1440)
    return if (s < e) n in s until e else n >= s || n < e
}

/**
 * Pure: next window edge (start or end) after [nowMs], as epoch ms.
 * The earliest future edge is always the next transition, overnight included. Tested.
 */
fun nextWindowEdgeMs(nowMs: Long, startMin: Int, endMin: Int): Long {
    val zone = ZoneId.systemDefault()
    val midnight = LocalDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val s = startMin.mod(1440) * 60_000L
    val e = endMin.mod(1440) * 60_000L
    val edges = listOf(midnight + s, midnight + e, midnight + s + 86_400_000L, midnight + e + 86_400_000L)
    return edges.filter { it > nowMs }.minOrNull() ?: (nowMs + 86_400_000L)
}

/** Pure: 480 -> "08:00". Tested. */
fun fmtWindowTime(min: Int): String {
    val m = min.mod(1440)
    return String.format("%02d:%02d", m / 60, m % 60)
}

/** Wall-clock minutes since midnight. */
fun nowMinuteOfDay(): Int {
    val t = LocalTime.now()
    return t.hour * 60 + t.minute
}

/** Self-perpetuating edge alarm: each fire enforces the window + schedules the next edge. */
class StandbyWindowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            enforceStandbyWindow(context)
            val store = Store(context.applicationContext)
            armStandbyWindow(
                context, store.wakeEnabled && store.standbyWinOn,
                store.standbyStart, store.standbyEnd
            )
        } catch (_: Exception) {
        }
    }
}

/** Enforce the window NOW: start inside, stop outside. No-op unless armed + window on. */
fun enforceStandbyWindow(ctx: Context) {
    try {
        val store = Store(ctx.applicationContext)
        if (!store.wakeEnabled || !store.standbyWinOn) return
        val inWin = inStandbyWindow(nowMinuteOfDay(), store.standbyStart, store.standbyEnd)
        if (inWin && !WakeService.isRunning) {
            val i = Intent(ctx, WakeService::class.java).setAction(WakeService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } else if (!inWin && WakeService.isRunning) {
            // Plain stop (NOT ACTION_STOP): wake stays armed for the next edge.
            ctx.stopService(Intent(ctx, WakeService::class.java))
        }
    } catch (_: Exception) {
    }
}

/** Arm (or cancel) the next window-edge alarm. Exact when permitted, idle-allowed otherwise. */
fun armStandbyWindow(ctx: Context, on: Boolean, startMin: Int, endMin: Int) {
    try {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, WINDOW_REQ, Intent(ctx, StandbyWindowReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!on) {
            runCatching { am.cancel(pi) }
            return
        }
        val at = nextWindowEdgeMs(System.currentTimeMillis(), startMin, endMin)
        val exactOk = if (Build.VERSION.SDK_INT >= 31) {
            try {
                am.canScheduleExactAlarms()
            } catch (_: Exception) {
                false
            }
        } else true
        if (exactOk) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    } catch (_: Exception) {
    }
}

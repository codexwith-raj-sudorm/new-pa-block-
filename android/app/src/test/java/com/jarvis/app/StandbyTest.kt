package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jarvis.app.backend.system.autoStartTarget
import com.jarvis.app.backend.system.fmtWindowTime
import com.jarvis.app.backend.system.inStandbyWindow
import com.jarvis.app.backend.system.nextWindowEdgeMs
import com.jarvis.app.backend.system.shouldRevive
import com.jarvis.app.backend.system.shouldStandbyToast
import com.jarvis.app.backend.system.standbyBackoffMs
import java.time.LocalDate
import java.time.ZoneId

class StandbyTest {
    @Test
    fun reviveOnlyWhenArmedButDead() {
        assertTrue(shouldRevive(true, false))
        assertFalse(shouldRevive(true, true))
        assertFalse(shouldRevive(false, false))
        assertFalse(shouldRevive(false, true))
    }

    @Test
    fun stormBackoff() {
        assertEquals(0L, standbyBackoffMs(0))
        assertEquals(0L, standbyBackoffMs(20))
        assertEquals(60_000L, standbyBackoffMs(21))
        assertEquals(60_000L, standbyBackoffMs(200))
    }

    @Test
    fun standbyToastThrottle() {
        assertTrue(shouldStandbyToast(30 * 60 * 1000L, 0L))
        assertFalse(shouldStandbyToast(29 * 60 * 1000L, 0L))
        assertFalse(shouldStandbyToast(1_000L, 1_000L))
    }

    @Test
    fun oemTargets() {
        assertEquals(
            "com.miui.securitycenter" to
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            autoStartTarget("Xiaomi")
        )
        assertEquals(
            "com.coloros.safecenter" to
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            autoStartTarget("oneplus")
        )
        assertEquals(
            "com.vivo.permissionmanager" to
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            autoStartTarget("VIVO")
        )
        assertNull(autoStartTarget("Google"))
        assertNull(autoStartTarget("Samsung"))
        assertNull(autoStartTarget(""))
    }

    @Test
    fun windowMembership() {
        // Day window 08:00-18:00, [start, end).
        assertFalse(inStandbyWindow(479, 480, 1080))
        assertTrue(inStandbyWindow(480, 480, 1080))
        assertTrue(inStandbyWindow(1079, 480, 1080))
        assertFalse(inStandbyWindow(1080, 480, 1080))
        // Overnight 22:00-06:00 wraps midnight.
        assertFalse(inStandbyWindow(1319, 1320, 360))
        assertTrue(inStandbyWindow(1320, 1320, 360))
        assertTrue(inStandbyWindow(0, 1320, 360))
        assertTrue(inStandbyWindow(359, 1320, 360))
        assertFalse(inStandbyWindow(360, 1320, 360))
        // Start == end means always on.
        assertTrue(inStandbyWindow(0, 480, 480))
        assertTrue(inStandbyWindow(1000, 480, 480))
    }

    @Test
    fun windowEdges() {
        val zone = ZoneId.systemDefault()
        fun at(h: Int, m: Int): Long =
            LocalDate.now(zone).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        // Day window: before -> start, inside -> end, after -> tomorrow's start.
        assertEquals(at(8, 0), nextWindowEdgeMs(at(7, 0), 480, 1080))
        assertEquals(at(18, 0), nextWindowEdgeMs(at(12, 0), 480, 1080))
        assertEquals(at(8, 0) + 86_400_000L, nextWindowEdgeMs(at(20, 0), 480, 1080))
        // Overnight: 23:00 -> 06:00 next day, 05:00 -> 06:00, 07:00 -> 22:00.
        assertEquals(at(6, 0) + 86_400_000L, nextWindowEdgeMs(at(23, 0), 1320, 360))
        assertEquals(at(6, 0), nextWindowEdgeMs(at(5, 0), 1320, 360))
        assertEquals(at(22, 0), nextWindowEdgeMs(at(7, 0), 1320, 360))
    }

    @Test
    fun windowFormat() {
        assertEquals("00:00", fmtWindowTime(0))
        assertEquals("08:00", fmtWindowTime(480))
        assertEquals("23:59", fmtWindowTime(1439))
        assertEquals("01:00", fmtWindowTime(1500))
    }
}

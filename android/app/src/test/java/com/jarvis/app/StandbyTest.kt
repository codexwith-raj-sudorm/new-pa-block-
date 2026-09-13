package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

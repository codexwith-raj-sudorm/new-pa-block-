package com.jarvis.app

import com.jarvis.app.ui.components.acousticBarCyan
import com.jarvis.app.ui.components.acousticBarHeight
import com.jarvis.app.ui.components.arcSpinMs
import com.jarvis.app.ui.components.coreStateLabel
import com.jarvis.app.ui.components.hudReadoutLine
import com.jarvis.app.ui.components.hudStatusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcHudTest {
    @Test
    fun statusLineCoversCombos() {
        assertEquals("NEURAL LINK ACTIVE", hudStatusLine(true, false))
        assertEquals("NEURAL LINK ACTIVE • WAKE ARMED", hudStatusLine(true, true))
        assertEquals("OFFLINE", hudStatusLine(false, false))
        assertEquals("OFFLINE • WAKE ARMED", hudStatusLine(false, true))
    }

    @Test
    fun stateLabelPriority() {
        assertEquals("STANDBY", coreStateLabel(false, false, false))
        assertEquals("THINKING", coreStateLabel(false, true, false))
        assertEquals("LISTENING", coreStateLabel(true, true, false))
        assertEquals("SPEAKING", coreStateLabel(true, true, true))
    }

    @Test
    fun spinSpeeds() {
        assertEquals(1200, arcSpinMs(true, false))
        assertEquals(2600, arcSpinMs(false, true))
        assertEquals(9000, arcSpinMs(false, false))
        assertEquals(1200, arcSpinMs(true, true))
    }

    @Test
    fun acousticBars() {
        for (i in 0 until 24) {
            val h = acousticBarHeight(0.7f, i)
            assertTrue(h >= 0.08f && h <= 1f)
            assertEquals(h, acousticBarHeight(0.7f, i), 0f)
            assertEquals(0.08f, acousticBarHeight(0f, i), 0f)
        }
        assertTrue(acousticBarCyan(0))
        assertTrue(acousticBarCyan(5))
        assertTrue(!acousticBarCyan(1))
        assertTrue(!acousticBarCyan(6))
    }

    @Test
    fun readoutFormats() {
        assertEquals("SYS 31°C • NET 42ms • PWR 87%", hudReadoutLine("31°C", "42ms", 87))
        assertEquals("SYS — • NET — • PWR —", hudReadoutLine("", "", -1))
        assertEquals("SYS 29°C • NET — • PWR 100%", hudReadoutLine("29°C", "", 100))
    }
}

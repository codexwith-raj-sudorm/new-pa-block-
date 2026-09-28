package com.jarvis.app

import com.jarvis.app.backend.brain.shareSummarySystem
import com.jarvis.app.backend.system.assistCaptureSettleMs
import com.jarvis.app.backend.system.assistantPkgOf
import com.jarvis.app.backend.system.toolYieldsScreen
import com.jarvis.app.frontend.screens.sharedImageQuestion
import org.junit.Assert.*
import org.junit.Test

class AssistModeTest {
    @Test fun parsesFlatComponent() {
        assertEquals("com.foo.bar", assistantPkgOf("com.foo.bar/.Assist"))
        assertEquals(
            "com.jarvis.app",
            assistantPkgOf("com.jarvis.app/com.jarvis.app.frontend.screens.AssistActivity")
        )
    }

    @Test fun blankIsNull() {
        assertNull(assistantPkgOf(null))
        assertNull(assistantPkgOf(""))
        assertNull(assistantPkgOf("   "))
    }

    @Test fun tolerantShapes() {
        assertEquals("com.foo", assistantPkgOf("  com.foo/.A  "))
        assertEquals("com.foo", assistantPkgOf("ComponentInfo{com.foo/.A}"))
        assertEquals("com.foo", assistantPkgOf("com.foo"))
    }

    @Test fun captureSettleIsPositive() {
        assertTrue(assistCaptureSettleMs() > 0)
    }

    @Test fun imageQuestions() {
        assertTrue("read" in sharedImageQuestion(true).lowercase())
        assertTrue("read" !in sharedImageQuestion(false).lowercase())
    }

    @Test fun yieldTools() {
        // "device" is granular (see deviceCmdYieldsScreen) — torch/silence keep the overlay.
        assertFalse(toolYieldsScreen("device"))
        assertTrue(toolYieldsScreen("access_tap"))
        assertTrue(toolYieldsScreen("autostart"))
        assertTrue(toolYieldsScreen("backup"))
        assertFalse(toolYieldsScreen("weather"))
        assertFalse(toolYieldsScreen("screen_watch"))
    }
}

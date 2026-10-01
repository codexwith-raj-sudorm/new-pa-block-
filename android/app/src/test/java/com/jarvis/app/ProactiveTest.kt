package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jarvis.app.backend.system.KIND_BATTERY
import com.jarvis.app.backend.system.KIND_MESSAGE
import com.jarvis.app.backend.system.PROACTIVE_FLOOR_MS
import com.jarvis.app.backend.system.isMessageNotif
import com.jarvis.app.backend.system.proactiveCooldownOk
import com.jarvis.app.backend.system.proactiveFallback
import com.jarvis.app.backend.system.proactivePrompt
import com.jarvis.app.backend.system.shouldSpeakProactive

class ProactiveTest {
    @Test
    fun messagePackages() {
        assertTrue(isMessageNotif("com.whatsapp"))
        assertTrue(isMessageNotif("com.whatsapp.w4b"))
        assertTrue(isMessageNotif("org.telegram.messenger"))
        assertTrue(isMessageNotif("com.google.android.gm"))
        assertTrue(isMessageNotif("com.google.android.apps.messaging"))
        assertFalse(isMessageNotif("com.instagram.android"))
        assertFalse(isMessageNotif("com.jarvis.app"))
        assertFalse(isMessageNotif(""))
    }

    @Test
    fun cooldownGate() {
        val hour = 3_600_000L
        // Fresh install speaks.
        assertTrue(proactiveCooldownOk(hour, 0L, "", "batt", 6 * hour))
        // 60s global floor stops storms even for new keys.
        assertTrue(proactiveCooldownOk(10 * hour, 10 * hour - PROACTIVE_FLOOR_MS, "a", "b", hour))
        assertFalse(proactiveCooldownOk(10 * hour, 10 * hour - PROACTIVE_FLOOR_MS + 1, "a", "b", hour))
        // New sender speaks past the floor.
        assertTrue(proactiveCooldownOk(10 * hour, 10 * hour - 120_000L, "msg:Ana", "msg:Raj", hour))
        // Repeat key waits out its cooldown.
        assertFalse(proactiveCooldownOk(10 * hour, 10 * hour - 120_000L, "batt", "batt", 6 * hour))
        assertTrue(proactiveCooldownOk(17 * hour, 10 * hour, "batt", "batt", 6 * hour))
    }

    @Test
    fun guardrails() {
        assertTrue(shouldSpeakProactive(false, false, false, false, false))
        assertFalse(shouldSpeakProactive(true, false, false, false, false))
        assertFalse(shouldSpeakProactive(false, true, false, false, false))
        assertFalse(shouldSpeakProactive(false, false, true, false, false))
        assertFalse(shouldSpeakProactive(false, false, false, true, false))
        assertFalse(shouldSpeakProactive(false, false, false, false, true))
    }

    @Test
    fun fallbackLines() {
        // Deterministic per slot, addressed to sir.
        assertEquals(
            "Sir, power reserves have dropped to 15 percent. You may want to connect a charger soon.",
            proactiveFallback(KIND_BATTERY, "15", 0)
        )
        assertTrue(proactiveFallback(KIND_BATTERY, "9", 1).contains("9 percent"))
        assertTrue(proactiveFallback(KIND_MESSAGE, "Ana", 0).contains("Ana"))
        assertTrue(proactiveFallback(KIND_MESSAGE, "Ana", 2).endsWith("sir."))
        // Slots wrap.
        assertEquals(
            proactiveFallback(KIND_BATTERY, "15", 0),
            proactiveFallback(KIND_BATTERY, "15", 3)
        )
    }

    @Test
    fun personaPrompt() {
        val p = proactivePrompt(KIND_BATTERY, "Battery low at 15 percent", "Raj")
        assertTrue(p.contains("Battery low at 15 percent"))
        assertTrue(p.contains("Raj"))
        assertTrue(p.contains("sir"))
        assertTrue(p.contains("Do not use emojis"))
    }
}

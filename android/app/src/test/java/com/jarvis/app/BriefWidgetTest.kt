package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class BriefWidgetTest {
    @Test fun noneLine() {
        assertEquals("No reminders", widgetReminderLine(null, 1_000_000L))
    }

    @Test fun nextLine() {
        val s = widgetReminderLine(ReminderItem(1, 1_000_000L + 5 * 60_000L, "stretch"), 1_000_000L)
        assertTrue(s.startsWith("Next: stretch "))
        assertTrue(s.contains("in 5 min"))
    }

    @Test fun truncatesLong() {
        val s = widgetReminderLine(ReminderItem(1, 1_000_000L + 60_000L, "x".repeat(60)), 1_000_000L)
        assertTrue(s.contains("x".repeat(30)))
        assertFalse(s.contains("x".repeat(31)))
    }
}

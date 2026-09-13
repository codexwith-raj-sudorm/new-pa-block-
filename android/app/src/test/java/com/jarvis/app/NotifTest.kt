package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class NotifTest {
    @Test fun emptyIsQuiet() {
        assertEquals("No notifications right now. All quiet.", formatNotifs(emptyList()))
    }

    @Test fun singleFormats() {
        val s = formatNotifs(listOf(NotifItem("com.whatsapp", "Mom", "call me", 1L)))
        assertTrue(s.startsWith("1 notification: "))
        assertTrue(s.contains("Whatsapp"))
        assertTrue(s.contains("Mom"))
    }

    @Test fun multiCapsAtFive() {
        val list = (1..8).map { NotifItem("com.app.x", "t$it", "b$it", it.toLong()) }
        val s = formatNotifs(list)
        assertTrue(s.startsWith("8 notifications: "))
        assertTrue(s.contains("t5"))
        assertFalse(s.contains("t6"))
    }
}

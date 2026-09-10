package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {
    @Test fun inMinutesParse() {
        val r = parseReminder("remind me in 10 minutes to drink water")!!
        assertEquals(InMinutes(10), r.whenAt)
        assertEquals("drink water", r.text)
    }

    @Test fun hoursParse() {
        val r = parseReminder("Remind me in 2 hours to call mom")!!
        assertEquals(InMinutes(120), r.whenAt)
        assertEquals("call mom", r.text)
    }

    @Test fun atParse() {
        val r = parseReminder("remind me at 5pm to go gym")!!
        assertEquals(AtTime(17, 0, false), r.whenAt)
        assertEquals("go gym", r.text)
    }

    @Test fun atAmTomorrowParse() {
        val r = parseReminder("remind me tomorrow at 9:30 am standup meeting")!!
        assertEquals(AtTime(9, 30, true), r.whenAt)
        assertEquals("standup meeting", r.text)
    }

    @Test fun garbageIsNull() {
        assertNull(parseReminder("remind me someday maybe"))
        assertNull(parseReminder("hello there"))
        assertNull(parseReminder("remind me"))
    }

    @Test fun dueTextBranches() {
        val now = 1_700_000_000_000L
        assertEquals("in 5 min", dueText(now + 5 * 60_000, now))
        assertEquals("in 2 h 30 min", dueText(now + 150 * 60_000, now))
        assertTrue(dueText(now + 3 * 24 * 3_600_000, now).startsWith("on "))
    }
}

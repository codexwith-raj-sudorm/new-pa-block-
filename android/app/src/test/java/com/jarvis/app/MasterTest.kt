package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class MasterTest {
    @Test fun identityNamesMaster() {
        val s = masterIdentity("Raj", "from Mumbai, loves chai")
        assertTrue(s.contains("Raj"))
        assertTrue(s.contains("Master"))
        assertTrue(s.contains("creator"))
        assertTrue(s.contains("Mumbai"))
    }

    @Test fun blankNameDefaults() {
        assertTrue(masterIdentity("", "").contains("Master"))
    }

    @Test fun blankAboutOmits() {
        assertFalse(masterIdentity("Raj", "").contains("know about"))
    }

    @Test fun cardRoundTrip() {
        val json = masterCardJson("s3cret", "Raj", "Mumbai")
        assertEquals(Triple("s3cret", "Raj", "Mumbai"), parseMasterCardJson(json))
    }

    @Test fun cardRejects() {
        assertNull(parseMasterCardJson("junk"))
        assertNull(parseMasterCardJson("""{"k":"ab","n":"x"}"""))
        assertNull(parseMasterCardJson("""{"n":"x"}"""))
    }

    @Test fun wakeGreetsMasterRaj() {
        assertEquals("Good morning, Master Raj.", wakeGreet(7, "Raj Thakur"))
        assertEquals("Good afternoon, Master Raj.", wakeGreet(13, "Raj Thakur"))
        assertEquals("Good evening, Master Raj.", wakeGreet(20, "Raj Thakur"))
        assertEquals("Good evening, Master Raj.", wakeGreet(2, "Raj Thakur"))
    }

    @Test fun wakeGreetBlankName() {
        assertEquals("Good morning, Master.", wakeGreet(9, ""))
    }

    @Test fun bucketsAndStamp() {
        assertEquals("morning", wakeBucket(5))
        assertEquals("morning", wakeBucket(11))
        assertEquals("afternoon", wakeBucket(12))
        assertEquals("afternoon", wakeBucket(16))
        assertEquals("evening", wakeBucket(17))
        assertEquals("evening", wakeBucket(4))
        assertEquals("2026-09-12-morning", wakeGreetStamp("2026-09-12", "morning"))
        assertEquals("Raj", firstName("  Raj  Thakur "))
    }

    @Test fun masterGreetPersonal() {
        assertEquals(
            "Welcome back, Master Raj. I am Jarvis, ready to serve.",
            masterGreet("Raj Thakur")
        )
        assertEquals(
            "Welcome back, Master Cher. I am Jarvis, ready to serve.",
            masterGreet("Cher")
        )
    }
}

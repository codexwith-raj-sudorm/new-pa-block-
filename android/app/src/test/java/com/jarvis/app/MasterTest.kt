package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test
import com.jarvis.app.backend.brain.masterCardJson
import com.jarvis.app.backend.brain.masterIdentity
import com.jarvis.app.backend.brain.parseMasterCardJson
import com.jarvis.app.backend.data.firstName
import com.jarvis.app.backend.data.masterGreet
import com.jarvis.app.backend.data.wakeBucket
import com.jarvis.app.backend.data.wakeGreet
import com.jarvis.app.backend.data.wakeGreetStamp
import com.jarvis.app.frontend.design.clearanceLabel

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

    @Test fun wakeGreetsSir() {
        assertEquals("Good morning, sir.", wakeGreet(7, "Raj Thakur"))
        assertEquals("Good afternoon, sir.", wakeGreet(13, "Raj Thakur"))
        assertEquals("Good evening, sir.", wakeGreet(20, "Raj Thakur"))
        assertEquals("Good evening, sir.", wakeGreet(2, "Raj Thakur"))
    }

    @Test fun wakeGreetBlankName() {
        assertEquals("Good morning, sir.", wakeGreet(9, ""))
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
            "Welcome back, sir. I am Jarvis, ready to serve.",
            masterGreet("Raj Thakur")
        )
        assertEquals(
            "Welcome back, sir. I am Jarvis, ready to serve.",
            masterGreet("Cher")
        )
    }

    @Test fun clearanceTiers() {
        assertEquals("OWNER", clearanceLabel(true, true))
        assertEquals("ADMIN", clearanceLabel(true, false))
        assertEquals("GUEST", clearanceLabel(false, false))
        assertEquals("GUEST", clearanceLabel(false, true))
    }
}

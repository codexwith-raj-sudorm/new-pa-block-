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
}

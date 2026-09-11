package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class RenameTest {
    @Test fun trims() {
        assertEquals("Hello", cleanTitle("  Hello  "))
    }

    @Test fun capsAt40() {
        assertEquals(40, cleanTitle("x".repeat(100)).length)
    }

    @Test fun blankDefaults() {
        assertEquals("New chat", cleanTitle("   "))
    }
}

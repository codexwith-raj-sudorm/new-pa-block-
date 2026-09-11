package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class VaultTest {
    @Test fun cutoffIs30Days() {
        assertEquals(1_000_000L - 2_592_000_000L, vaultCutoff(1_000_000L))
    }

    @Test fun migrationFreshStamps() {
        val rows = migrateLegacyFacts(setOf("likes chai", "from Mumbai"), 555L)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.ts == 555L })
        assertEquals(setOf("likes chai", "from Mumbai"), rows.map { it.text }.toSet())
    }

    @Test fun migrationTruncates() {
        val rows = migrateLegacyFacts(setOf("x".repeat(900)), 1L)
        assertEquals(500, rows[0].text.length)
    }
}

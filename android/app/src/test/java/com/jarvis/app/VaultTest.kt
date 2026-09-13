package com.jarvis.app

import com.jarvis.app.local.starkVaultCutoff
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

    @Test fun starkCutoff90() {
        assertEquals(0L, starkVaultCutoff(90L * 24 * 3600 * 1000, 90))
    }

    @Test fun starkCutoffCustom() {
        assertEquals(1000L, starkVaultCutoff(1000L + 24 * 3600 * 1000L, 1))
    }
}

package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class BriefingTest {
    @Test fun rowsFormat() {
        val rows = formatBriefing(Briefing(87, true, 1500, 4000, 12.3, 64.0, "Wi-Fi")).toMap()
        assertEquals("87% (charging)", rows["Battery"])
        assertEquals("1500 / 4000 MB", rows["Memory"])
        assertEquals("Wi-Fi", rows["Network"])
    }

    @Test fun storageRounds() {
        val rows = formatBriefing(Briefing(50, false, 1, 2, 3.24, 63.9, "offline")).toMap()
        assertEquals("3.2 / 64 GB", rows["Storage free"])
    }

    @Test fun notChargingOmitsTag() {
        val rows = formatBriefing(Briefing(10, false, 1, 2, 1.0, 8.0, "mobile data")).toMap()
        assertEquals("10%", rows["Battery"])
    }
}

package com.jarvis.app

import com.jarvis.app.frontend.design.configNetLabel
import com.jarvis.app.frontend.design.githubBadge
import com.jarvis.app.frontend.design.masterCardTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigPanelTest {
    @Test
    fun netLabelReflectsOnline() {
        assertEquals("Network Secure", configNetLabel(true))
        assertEquals("Offline Mode", configNetLabel(false))
    }

    @Test
    fun cardTitleReflectsInstall() {
        assertEquals("Master Mode Active", masterCardTitle(true))
        assertEquals("Install Master Key", masterCardTitle(false))
    }

    @Test
    fun badgePrefersExplicitStatus() {
        assertEquals("ok", githubBadge("ok", true))
        assertEquals("ok", githubBadge("ok", false))
        assertEquals("✓ Active via Master", githubBadge("", true))
        assertEquals("✓ Active via Master", githubBadge("  ", true))
        assertNull(githubBadge("", false))
    }
}

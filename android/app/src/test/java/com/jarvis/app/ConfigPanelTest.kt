package com.jarvis.app

import com.jarvis.app.frontend.design.configNetLabel
import com.jarvis.app.frontend.design.githubBadge
import com.jarvis.app.frontend.design.masterBrainLine
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
        assertEquals("Admin Mode Active", masterCardTitle(true))
        assertEquals("Private Admin", masterCardTitle(false))
    }

    @Test
    fun masterBrainLineShowsPrivateKeyState() {
        assertEquals("PRIVATE BRAIN — add GEMINI_API_KEY to local.properties", masterBrainLine(false, builtin = false, online = false))
        assertEquals("PRIVATE BRAIN — add GEMINI_API_KEY to local.properties", masterBrainLine(true, builtin = false, online = false))
        assertEquals("PRIVATE BRAIN — custom key active", masterBrainLine(true, builtin = false, online = true))
        assertEquals("PRIVATE BRAIN — built-in key active", masterBrainLine(true, builtin = true, online = true))
    }

    @Test
    fun badgePrefersExplicitStatus() {
        assertEquals("ok", githubBadge("ok", true))
        assertEquals("ok", githubBadge("ok", false))
        assertNull(githubBadge("", true))
        assertNull(githubBadge("  ", true))
        assertNull(githubBadge("", false))
    }
}

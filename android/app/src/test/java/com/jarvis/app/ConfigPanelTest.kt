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
        assertEquals("Master Mode Active", masterCardTitle(true))
        assertEquals("Install Master Key", masterCardTitle(false))
    }

    @Test
    fun masterBrainLineSeparatesByokFromPrivateBuiltIn() {
        assertEquals("NO MASTER — paste a Gemini key below", masterBrainLine(false, builtin = false, online = false))
        assertEquals("MASTER MODE — this build has no built-in key; paste yours below", masterBrainLine(true, builtin = false, online = false))
        assertEquals("MASTER MODE — AI brain online", masterBrainLine(true, builtin = false, online = true))
        assertEquals("MASTER MODE — private/dev brain unlocked", masterBrainLine(true, builtin = true, online = true))
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

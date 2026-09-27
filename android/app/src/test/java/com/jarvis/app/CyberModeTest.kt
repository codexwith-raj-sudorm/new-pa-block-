package com.jarvis.app

import androidx.compose.ui.graphics.Color
import com.jarvis.app.frontend.design.CyberGreen
import com.jarvis.app.frontend.design.cyberBootLines
import com.jarvis.app.frontend.design.cyberPrompt
import com.jarvis.app.frontend.design.cyberScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CyberModeTest {
    @Test fun promptFormatsUserAtHost() {
        assertEquals("raj@jarvis:~\$", cyberPrompt("raj"))
    }

    @Test fun promptFallsBackToMaster() {
        assertEquals("master@jarvis:~\$", cyberPrompt(""))
        assertEquals("master@jarvis:~\$", cyberPrompt("   "))
        assertEquals("master@jarvis:~\$", cyberPrompt("  master  "))
    }

    @Test fun cyberSchemeIsGreenOnBlack() {
        val scheme = cyberScheme()
        assertEquals(CyberGreen, scheme.primary)
        assertEquals(Color.Black, scheme.onPrimary)
    }

    @Test fun bootBannerIsPresentable() {
        val lines = cyberBootLines()
        assertTrue(lines.size >= 3)
        assertTrue(lines.all { it.isNotBlank() })
        assertTrue(lines[0].contains("JARVIS"))
        assertTrue(lines[0].contains("v5.9"))
    }
}

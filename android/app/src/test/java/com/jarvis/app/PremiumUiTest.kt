package com.jarvis.app

import com.jarvis.app.frontend.design.avatarLetter
import com.jarvis.app.frontend.design.blobPointRadius
import com.jarvis.app.frontend.design.modelShortName
import com.jarvis.app.frontend.design.premiumHeroVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PremiumUiTest {
    @Test
    fun avatarLetterUppercasesFirst() {
        assertEquals("R", avatarLetter("Raj Thakur"))
        assertEquals("J", avatarLetter("  "))
        assertEquals("J", avatarLetter(""))
    }

    @Test
    fun heroUntilUserSpeaks() {
        assertTrue(premiumHeroVisible(0, false))
        assertFalse(premiumHeroVisible(1, false))
        assertFalse(premiumHeroVisible(2, false))
        assertFalse(premiumHeroVisible(0, true))
    }

    @Test
    fun modelShortNameStripsPath() {
        assertEquals("gemini-2.5-flash", modelShortName("models/gemini-2.5-flash"))
        assertEquals("default", modelShortName(""))
    }

    @Test
    fun blobRadiusOscillatesAroundBase() {
        val base = 100f
        // phase 0, angle 0 -> cos(0) = 1 -> max
        assertEquals(110f, blobPointRadius(base, 0f, 3, 0.1f, 0f), 0.001f)
        // half period of the 3-lobe wave -> min
        assertEquals(90f, blobPointRadius(base, (PI / 3).toFloat(), 3, 0.1f, 0f), 0.01f)
    }
}

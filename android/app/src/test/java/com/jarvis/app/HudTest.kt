package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HudTest {
    @Test fun accentStates() {
        assertEquals(0xFF22D3EE, hudAccentArgb(false, false, false, true))
        assertEquals(0xFF39FF6A, hudAccentArgb(true, false, false, true))
        assertEquals(0xFFF59E0B, hudAccentArgb(false, true, false, true))
        assertEquals(0xFFF8FAFC, hudAccentArgb(false, false, true, true))
        assertEquals(0xFF64748B, hudAccentArgb(false, false, false, false))
    }

    @Test fun accentPriority() {
        assertEquals(0xFFF59E0B, hudAccentArgb(true, true, true, true))
        assertEquals(0xFF39FF6A, hudAccentArgb(true, false, true, false))
        assertEquals(0xFFF8FAFC, hudAccentArgb(false, false, true, false))
    }

    @Test fun waveSilentStaysZero() {
        assertEquals(0f, waveBarEnergy(3, 24, 0f, 0f), 0f)
        assertEquals(0f, waveBarEnergy(0, 24, 0f, 180f), 0f)
    }

    @Test fun waveLiveVaries() {
        val a = waveBarEnergy(0, 24, 1f, 0f)
        val b = waveBarEnergy(5, 24, 1f, 0f)
        assertTrue(a in 0f..1f)
        assertTrue(b in 0f..1f)
        assertNotEquals(a, b)
        assertEquals(a, waveBarEnergy(0, 24, 1f, 0f), 0f)
    }

    @Test fun clickPcmShape() {
        val pcm = genClickPcm()
        assertEquals(1984, pcm.size)
        assertTrue(pcm.any { it != 0.toShort() })
        val head = pcm.take(pcm.size / 4).maxOf { abs(it.toInt()) }
        val tail = pcm.takeLast(pcm.size / 4).maxOf { abs(it.toInt()) }
        assertTrue("no decay: $head vs $tail", head > tail)
    }

    @Test fun chimePcmShape() {
        val pcm = genChimePcm()
        assertEquals(13230, pcm.size)
        assertTrue(pcm.any { it != 0.toShort() })
        val head = pcm.take(pcm.size / 4).maxOf { abs(it.toInt()) }
        val tail = pcm.takeLast(pcm.size / 4).maxOf { abs(it.toInt()) }
        assertTrue("no decay: $head vs $tail", head > tail)
    }
}

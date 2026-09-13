package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioReactiveTest {
    @Test fun silenceRmsStaysAtZero() {
        assertEquals(0f, normalizeRms(-2f), 0.001f)
        assertEquals(0f, normalizeRms(0f), 0.001f)
        assertEquals(0f, normalizeRms(1f), 0.001f)
    }

    @Test fun speechRmsRamps() {
        assertEquals(0.5f, normalizeRms(5.5f), 0.01f)
    }

    @Test fun loudRmsClampsToOne() {
        assertEquals(1f, normalizeRms(10f), 0.001f)
        assertEquals(1f, normalizeRms(25f), 0.001f)
    }
}

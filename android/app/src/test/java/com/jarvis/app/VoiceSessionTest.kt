package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionTest {
    @Test
    fun nearbyVoiceGate() {
        assertFalse(isNearbyVoice(0f))
        assertFalse(isNearbyVoice(1.5f))
        assertFalse(isNearbyVoice(NEARBY_RMS_DB - 0.1f))
        assertTrue(isNearbyVoice(NEARBY_RMS_DB))
        assertTrue(isNearbyVoice(7f))
    }

    @Test
    fun convoWindowIsTenSeconds() {
        assertEquals(10_000L, CONVO_SILENCE_MS)
        assertFalse(convoExpired(10_000L, 1_000L))
        assertFalse(convoExpired(10_999L, 1_000L))
        assertTrue(convoExpired(11_000L, 1_000L))
        assertTrue(convoExpired(25_000L, 1_000L))
    }
}

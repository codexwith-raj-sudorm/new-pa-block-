package com.jarvis.app

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLoopTest {
    @Test
    fun clientErrorIsSilent() {
        assertNull(voiceErrorText(SpeechRecognizer.ERROR_CLIENT))
    }

    @Test
    fun errorsAreHumanNoCodes() {
        assertEquals("Didn't catch that — try again", voiceErrorText(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("Didn't catch that — try again", voiceErrorText(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals("Mic is busy — try again in a second", voiceErrorText(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals("Voice needs internet — check your connection", voiceErrorText(SpeechRecognizer.ERROR_NETWORK))
        assertEquals("Voice service hiccup — try again", voiceErrorText(SpeechRecognizer.ERROR_SERVER))
        assertEquals("Couldn't open the mic — try again", voiceErrorText(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(
            "Mic permission needed for voice input",
            voiceErrorText(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
        val unknown = voiceErrorText(999)!!
        assertFalse(unknown.contains("("))
        assertFalse(unknown.contains("999"))
    }

    @Test
    fun bestHeardPicksConfidence() {
        assertEquals("hello", bestHeard(listOf("hello", "hollow"), floatArrayOf(0.9f, 0.2f)))
        assertEquals("b", bestHeard(listOf("a", "b"), floatArrayOf(0.1f, 0.8f)))
    }

    @Test
    fun bestHeardSkipsBlanks() {
        assertEquals("hi", bestHeard(listOf("  ", "hi"), floatArrayOf(0.9f, 0.1f)))
        assertEquals("", bestHeard(listOf(" ", ""), null))
        assertEquals("x", bestHeard(listOf("x"), null))
        assertEquals("", bestHeard(null, null))
    }

    @Test
    fun stuckSpeech() {
        assertTrue(speakingStuck(true, 100_000L, 0L))
        assertFalse(speakingStuck(true, 10_000L, 0L))
        assertFalse(speakingStuck(false, 100_000L, 0L))
    }
}

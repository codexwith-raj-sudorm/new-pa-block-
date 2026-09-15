package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWatchTest {

    @Test
    fun promptReadsWhenAsked() {
        assertTrue(visionPromptFor("read my screen").contains("Transcribe"))
        assertTrue(visionPromptFor("what text is on my screen").contains("Transcribe"))
        assertTrue(visionPromptFor("what's on my screen").contains("Describe"))
        assertTrue(visionPromptFor("look at my screen").contains("Describe"))
    }

    @Test
    fun repromptMatcher() {
        assertTrue(watchErrorNeedsReprompt("no consent — approve the prompt"))
        assertTrue(watchErrorNeedsReprompt("projection null — approve the prompt again"))
        assertTrue(watchErrorNeedsReprompt("SecurityException: Media projections require a foreground service"))
        assertFalse(watchErrorNeedsReprompt("declined"))
        assertFalse(watchErrorNeedsReprompt("no frame — try again"))
        assertFalse(watchErrorNeedsReprompt("timed out"))
    }
}

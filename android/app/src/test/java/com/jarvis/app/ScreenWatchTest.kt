package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jarvis.app.backend.system.screenVisionPackageBlocked
import com.jarvis.app.backend.system.visionPromptFor
import com.jarvis.app.backend.system.watchErrorNeedsReprompt

class ScreenWatchTest {

    @Test
    fun promptReadsWhenAsked() {
        assertTrue(visionPromptFor("read my screen").contains("Transcribe"))
        assertTrue(visionPromptFor("what text is on my screen").contains("Transcribe"))
        assertTrue(visionPromptFor("what's on my screen").contains("Describe"))
        assertTrue(visionPromptFor("look at my screen").contains("Describe"))
    }

    @Test
    fun sensitivePackagesAreBlocked() {
        assertTrue(screenVisionPackageBlocked("com.google.android.apps.authenticator2"))
        assertTrue(screenVisionPackageBlocked("com.phonepe.app"))
        assertTrue(screenVisionPackageBlocked("com.example.mybanking"))
        assertFalse(screenVisionPackageBlocked("com.android.chrome"))
        assertFalse(screenVisionPackageBlocked(null))
    }

    @Test
    fun repromptMatcher() {
        assertTrue(watchErrorNeedsReprompt("no consent — approve the prompt"))
        assertTrue(watchErrorNeedsReprompt("projection null — approve the prompt again"))
        assertTrue(watchErrorNeedsReprompt("SecurityException: Media projections require a foreground service"))
        assertFalse(watchErrorNeedsReprompt("declined"))
        assertFalse(watchErrorNeedsReprompt("no frame — try again"))
        assertFalse(watchErrorNeedsReprompt("screen is blank or protected by Android security controls"))
        assertFalse(watchErrorNeedsReprompt("timed out"))
    }
}

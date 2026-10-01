package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jarvis.app.backend.brain.MEDIA_PLATFORM_MUSIC
import com.jarvis.app.backend.brain.MEDIA_PLATFORM_YOUTUBE
import com.jarvis.app.backend.brain.MEDIA_PREF_SYSTEM
import com.jarvis.app.backend.brain.PKG_SPOTIFY
import com.jarvis.app.backend.brain.PKG_YOUTUBE
import com.jarvis.app.backend.brain.PlayMedia
import com.jarvis.app.backend.brain.mediaAppLabel
import com.jarvis.app.backend.brain.mediaAck
import com.jarvis.app.backend.brain.mediaChoices
import com.jarvis.app.backend.brain.parsePlayMedia
import com.jarvis.app.backend.brain.pickMediaPackage
import com.jarvis.app.backend.brain.youtubeSearchUrl

class PlaybackTest {
    @Test
    fun parseCommand() {
        assertEquals(
            PlayMedia(MEDIA_PLATFORM_MUSIC, "West Coast Lana Del Rey"),
            parsePlayMedia("{\"action\": \"play_media\", \"platform\": \"music\", \"query\": \"West Coast Lana Del Rey\"}")
        )
        assertEquals(
            PlayMedia(MEDIA_PLATFORM_YOUTUBE, "lofi beats"),
            parsePlayMedia("{\"action\":\"play_media\",\"platform\":\"YouTube\",\"query\":\"lofi beats\"}")
        )
        // Tolerates prose around the JSON.
        assertEquals(
            PlayMedia(MEDIA_PLATFORM_MUSIC, "Believer"),
            parsePlayMedia("Sure! {\"action\": \"play_media\", \"platform\": \"music\", \"query\": \"Believer\"} Enjoy.")
        )
        // Unknown platform falls back to music.
        assertEquals(
            PlayMedia(MEDIA_PLATFORM_MUSIC, "Believer"),
            parsePlayMedia("{\"action\": \"play_media\", \"platform\": \"radio\", \"query\": \"Believer\"}")
        )
        // Plain chat and junk stay null.
        assertNull(parsePlayMedia("Hello there, how are you?"))
        assertNull(parsePlayMedia("{\"action\": \"chat\", \"query\": \"x\"}"))
        assertNull(parsePlayMedia("{\"action\": \"play_media\", \"platform\": \"music\"}"))
        assertNull(parsePlayMedia("not json at all"))
    }

    @Test
    fun ackAndLabels() {
        assertEquals("Loading track, sir.", mediaAck(MEDIA_PLATFORM_MUSIC))
        assertEquals("Routing to YouTube.", mediaAck(MEDIA_PLATFORM_YOUTUBE))
        assertEquals("Spotify", mediaAppLabel(PKG_SPOTIFY))
        assertEquals("YouTube", mediaAppLabel(PKG_YOUTUBE))
        assertEquals("System default", mediaAppLabel(MEDIA_PREF_SYSTEM))
        assertEquals("Ask every time", mediaAppLabel(""))
    }

    @Test
    fun packagePick() {
        val all = setOf(PKG_SPOTIFY, PKG_YOUTUBE, "com.google.android.apps.youtube.music")
        // Remembered pref wins when installed.
        assertEquals(PKG_SPOTIFY, pickMediaPackage(MEDIA_PLATFORM_MUSIC, PKG_SPOTIFY, all))
        assertEquals(MEDIA_PREF_SYSTEM, pickMediaPackage(MEDIA_PLATFORM_MUSIC, MEDIA_PREF_SYSTEM, all))
        // Gone app re-asks.
        assertNull(pickMediaPackage(MEDIA_PLATFORM_MUSIC, PKG_SPOTIFY, setOf(PKG_YOUTUBE)))
        // Blank pref asks (music) but YouTube with one target fires direct.
        assertNull(pickMediaPackage(MEDIA_PLATFORM_MUSIC, "", all))
        assertEquals(PKG_YOUTUBE, pickMediaPackage(MEDIA_PLATFORM_YOUTUBE, "", all))
        assertNull(pickMediaPackage(MEDIA_PLATFORM_YOUTUBE, "", emptySet()))
    }

    @Test
    fun choicesAndFallbackUrl() {
        assertTrue(mediaChoices(MEDIA_PLATFORM_MUSIC, setOf(PKG_SPOTIFY)).contains(PKG_SPOTIFY))
        assertTrue(mediaChoices(MEDIA_PLATFORM_MUSIC, setOf(PKG_SPOTIFY)).size == 1)
        assertTrue(
            youtubeSearchUrl("lofi beats").startsWith("https://www.youtube.com/results?search_query=")
        )
        assertTrue(youtubeSearchUrl("a b").contains("a+b"))
    }
}

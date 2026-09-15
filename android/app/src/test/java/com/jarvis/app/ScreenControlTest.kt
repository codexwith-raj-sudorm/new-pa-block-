package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenControlTest {
    @Test
    fun tapPickRanks() {
        val texts = listOf("Send", "Send feedback", "Cancel")
        assertEquals(0, tapPick(texts, "send").index)
        assertEquals(2, tapPick(texts, "cancel").index)
        assertEquals(2, tapPick(texts, "canc").index)
        val amb = tapPick(texts, "end")
        assertNull(amb.index)
        assertEquals(2, amb.options.size)
        assertEquals(0, tapPick(texts, "send").options.size)
        assertTrue(tapPick(texts, "").index == null)
        assertTrue(tapPick(texts, "zzz").options.isEmpty())
    }

    @Test
    fun tapPickAmbiguousListsOptions() {
        val texts = listOf("Open chat", "Open settings", "Close")
        val amb = tapPick(texts, "open")
        assertNull(amb.index)
        assertEquals(2, amb.options.size)
    }

    @Test
    fun routerRoutesScreen() {
        assertEquals("shot", Router.detect("take a screenshot")?.tool)
        assertEquals("shot", Router.detect("screenshot")?.tool)
        assertEquals("shot", Router.detect("share my screen")?.tool)
        assertEquals("screen_watch", Router.detect("what's on my screen")?.tool)
        assertEquals("screen_watch", Router.detect("read my screen")?.tool)
        assertEquals("access_tap", Router.detect("tap send")?.tool)
        assertEquals("send", Router.detect("tap send")?.arg)
        assertEquals("access_scroll", Router.detect("scroll up")?.tool)
        assertEquals("access_back", Router.detect("go back")?.tool)
        assertEquals("access_setup", Router.detect("enable accessibility")?.tool)
    }

    @Test
    fun screenDoesNotHijack() {
        assertNull(Router.detect("go back to sleep"))
        assertNull(Router.detect("scroll up a bit"))
        assertNull(Router.detect("read the news"))
        assertEquals("whatsnew", Router.detect("what's new")?.tool)
        assertEquals("device", Router.detect("open jarvis app")?.tool)
        assertEquals("notifs", Router.detect("read my notifications")?.tool)
    }
}

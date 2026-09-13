package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class ExportTest {
    @Test fun transcriptFormats() {
        val t = chatTranscript(
            "Hello",
            listOf(ChatMessage("user", "hi"), ChatMessage("bot", "hey"))
        )
        assertTrue(t.startsWith("JARVIS - Hello"))
        assertTrue(t.contains("You: hi"))
        assertTrue(t.contains("Jarvis: hey"))
    }

    @Test fun headerCarriesFormatTag() {
        val t = chatTranscript("Hello", listOf(ChatMessage("bot", "x")))
        assertTrue(t.contains("j5-f4e3e575"))
        assertEquals("f4e3e575", zwRead(t))
    }

    @Test fun tagCodecRoundTrip() {
        assertEquals("f4e3e575", zwRead(zwBits("f4e3e575")))
        assertEquals("", zwRead("plain text, no bits"))
    }

    @Test fun blankTitleDefaults() {
        val t = chatTranscript("", listOf(ChatMessage("bot", "x")))
        assertTrue(t.startsWith("JARVIS - Chat"))
    }
}

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

    @Test fun blankTitleDefaults() {
        val t = chatTranscript("", listOf(ChatMessage("bot", "x")))
        assertTrue(t.startsWith("JARVIS - Chat"))
    }
}

package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jarvis.app.backend.ai.isLocalOrPrivateHost
import com.jarvis.app.backend.ai.jsonEscape
import com.jarvis.app.backend.ai.openAiBaseValidationError
import com.jarvis.app.backend.ai.openAiChatBody
import com.jarvis.app.backend.ai.openAiEndpoint

class AiProvidersTest {
    @Test fun endpointDefaultsToOpenAi() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            openAiEndpoint("")
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            openAiEndpoint("   ")
        )
    }

    @Test fun endpointNormalizesCustomBase() {
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            openAiEndpoint("https://api.groq.com/openai/v1/")
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            openAiEndpoint("https://api.groq.com/openai/v1/chat/completions")
        )
    }

    @Test fun endpointValidationBlocksLocalReleaseTargets() {
        assertEquals(null, openAiBaseValidationError(""))
        assertEquals(null, openAiBaseValidationError("https://api.groq.com/openai/v1"))
        assertTrue(isLocalOrPrivateHost("localhost"))
        assertTrue(isLocalOrPrivateHost("127.0.0.1"))
        assertTrue(isLocalOrPrivateHost("192.168.1.5"))
        assertTrue(openAiBaseValidationError("http://localhost:11434/v1")!!.contains("https"))
        assertTrue(openAiBaseValidationError("https://127.0.0.1:11434/v1")!!.contains("Local/private"))
        assertEquals(null, openAiBaseValidationError("http://localhost:11434/v1", allowLocal = true))
    }

    @Test fun escapeHandlesQuotesAndNewlines() {
        assertEquals("a\\\"b\\\\c\\nd", jsonEscape("a\"b\\c\nd"))
    }

    @Test fun bodyMapsRolesAndEscapes() {
        val body = openAiChatBody(
            "gpt-4o-mini", "be brief",
            listOf("user" to "hi", "model" to "hello"),
            "say \"ok\""
        )
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(body.contains("\"role\":\"system\",\"content\":\"be brief\""))
        assertTrue(body.contains("\"role\":\"assistant\",\"content\":\"hello\""))
        assertTrue(body.contains("say \\\"ok\\\""))
    }

    @Test fun bodyCapsHistoryAt20() {
        val hist = (1..30).map { "user" to "m$it" }
        val body = openAiChatBody("m", "s", hist, "u")
        assertTrue(!body.contains("\"content\":\"m1\""))
        assertTrue(body.contains("\"content\":\"m30\""))
    }
}

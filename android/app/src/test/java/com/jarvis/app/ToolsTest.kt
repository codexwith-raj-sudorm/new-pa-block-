package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class ToolsTest {
    @Test fun calcBasic() = assertEquals(8.0, Calculator.evaluate("2+2*3"), 1e-9)

    @Test fun calcPercent() = assertEquals(36.0, Calculator.evaluate("15% of 240"), 1e-9)

    @Test fun calcPower() = assertEquals(8.0, Calculator.evaluate("2^3"), 1e-9)

    @Test fun calcSqrt() = assertEquals(4.0, Calculator.evaluate("sqrt(16)"), 1e-9)

    @Test fun calcMult() = assertEquals(144.0, Calculator.evaluate("12*12"), 1e-9)

    @Test fun calcRejectsGarbage() {
        try {
            Calculator.evaluate("foo(1)")
            fail("should throw")
        } catch (_: Exception) {
        }
    }

    @Test fun routerTime() = assertEquals("time", Router.detect("what time is it?")?.tool)

    @Test fun routerCalc() = assertEquals("calc", Router.detect("calc 2+2")?.tool)

    @Test fun routerMath() = assertEquals("calc", Router.detect("What is 12*12?")?.tool)

    @Test fun routerWordsIgnored() = assertNull(Router.detect("What is the capital of France?"))

    @Test fun routerRemember() = assertEquals("remember", Router.detect("remember I like tea")?.tool)

    @Test fun routerChatIgnored() = assertNull(Router.detect("hello there"))

    @Test fun pickPreferredFirst() {
        val picked = GeminiApi.pickModels(
            "gemini-2.0-flash",
            listOf("gemini-1.5-pro", "gemini-2.0-flash", "gemini-2.0-flash-lite")
        )
        assertEquals("gemini-2.0-flash", picked[0])
    }

    @Test fun pickSortsFlashLiteFirst() {
        val picked = GeminiApi.pickModels(
            "unknown-model",
            listOf("gemini-2.0-pro", "gemini-2.0-flash", "gemini-2.0-flash-lite")
        )
        assertEquals(
            listOf("gemini-2.0-flash-lite", "gemini-2.0-flash", "gemini-2.0-pro"),
            picked
        )
    }

    @Test fun parseModels() {
        val json = """{"models":[
            {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]},
            {"name":"models/gemini-x","supportedGenerationMethods":["generateContent"]}
        ]}"""
        assertEquals(listOf("gemini-2.0-flash", "gemini-x"), GeminiApi.parseModelNames(json))
    }

    @Test fun parseModelsBadJson() = assertTrue(GeminiApi.parseModelNames("nope").isEmpty())

    @Test fun parseModelsExcludesTtsAndEmbeddings() {
        val json = """{"models":[
            {"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["generateContent","embedContent"]},
            {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]}
        ]}"""
        assertEquals(listOf("gemini-2.0-flash"), GeminiApi.parseModelNames(json))
    }

    @Test fun pickModelsSkipsTts() {
        val picked = GeminiApi.pickModels(
            "unknown",
            listOf("gemini-2.5-flash-preview-tts", "gemini-2.0-flash", "text-embedding-004")
        )
        assertEquals(listOf("gemini-2.0-flash"), picked)
    }

    @Test fun chatTitleFromFirstUser() {
        assertEquals(
            "hello world",
            chatTitle(listOf("user" to "hello world", "model" to "hi"))
        )
    }

    @Test fun chatTitleSkipsBotFirst() {
        assertEquals("abc", chatTitle(listOf("model" to "greet", "user" to "abc")))
    }

    @Test fun chatTitleLong() {
        val long = "this is a very long first message indeed yes"
        assertEquals(long.take(32).trimEnd() + "…", chatTitle(listOf("user" to long)))
    }

    @Test fun chatTitleEmpty() {
        assertEquals("New chat", chatTitle(emptyList()))
        assertEquals("New chat", chatTitle(listOf("model" to "hi")))
    }

    @Test fun cleanSpeechStripsEmoji() {
        val wave = "Hello 👋 • world"
        println("DIAG wave codes=" + wave.map { it.code.toString(16) }.joinToString(","))
        println("DIAG r1=" + Regex("[\\uD83C-\\uDBFF]").containsMatchIn(wave))
        println("DIAG r2=" + Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]+").containsMatchIn(wave))
        val actual = cleanForSpeech(wave)
        println("DIAG actual=[$actual] codes=" + actual.map { it.code.toString(16) }.joinToString(","))
        assertTrue("see DIAG lines", actual == "Hello , world")
    }

    @Test fun cleanSpeechLinks() {
        assertEquals("see link now", cleanForSpeech("see https://x.io/a now"))
    }

    @Test fun splitKeepsDanda() {
        val parts = splitSentences("তুমি কেমন আছ। আমি ভালো।")
        assertEquals(2, parts.size)
    }

    @Test fun splitLong() {
        val s = "word ".repeat(500)
        val parts = splitSentences(s)
        assertTrue(parts.size >= 2)
        assertTrue(parts.all { it.length <= 1500 })
    }
}

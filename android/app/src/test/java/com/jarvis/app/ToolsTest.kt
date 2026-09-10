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
}

package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class FxTest {
    @Test fun parsesPairs() {
        assertEquals(Triple(100.0, "USD", "INR"), parseCurrency("100 dollars in rupees"))
        assertEquals(Triple(50.0, "EUR", "USD"), parseCurrency("convert 50 eur to usd"))
    }

    @Test fun rejects() {
        assertNull(parseCurrency("100 dollars in dollars"))
        assertNull(parseCurrency("100 bitcoins in rupees"))
        assertNull(parseCurrency("hello"))
    }

    @Test fun rateParses() {
        assertEquals(83.2, parseFxRate("""{"rates":{"INR":83.2}}""", "INR")!!, 0.001)
        assertNull(parseFxRate("junk", "INR"))
    }

    @Test fun formats() {
        assertEquals("1 USD = 0.5 EUR", formatFx(1.0, "USD", 0.5, "EUR"))
        val big = formatFx(100.0, "USD", 83.205, "INR")
        assertTrue(big.startsWith("100 USD = "))
        assertTrue(big.endsWith("INR"))
    }

    @Test fun percentWord() {
        assertEquals(36.0, Calculator.evaluate("15 percent of 240"), 0.001)
    }
}

package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class FunTest {
    @Test fun convertLength() {
        assertEquals("5 mi = 8.05 km", convertUnits("convert 5 miles to km"))
    }

    @Test fun convertTemp() {
        assertEquals("32 f = 0 c", convertUnits("32 f to c"))
        assertEquals("100 c = 212 f", convertUnits("convert 100 C to F"))
    }

    @Test fun convertMass() {
        assertEquals("1 kg = 2.2 lb", convertUnits("1 kg to lb"))
    }

    @Test fun convertRejects() {
        assertNull(convertUnits("5 parsecs to km"))
        assertNull(convertUnits("5 kg to km"))
        assertNull(convertUnits("hello there"))
    }

    @Test fun diceParse() {
        assertEquals(20, parseDice("roll a d20"))
        assertEquals(6, parseDice("roll dice"))
        assertEquals(6, parseDice("roll a d1"))
    }

    @Test fun dieRollsInRange() {
        assertEquals(1, rollDie(6, 0.0))
        assertEquals(6, rollDie(6, 0.999))
        assertEquals(20, rollDie(20, 0.999))
    }

    @Test fun coinFaces() {
        assertEquals("Heads.", coinFace(true))
        assertEquals("Tails.", coinFace(false))
    }

    @Test fun jokesWrap() {
        assertEquals(jokeAt(0), jokeAt(8))
        assertTrue(jokeAt(3).isNotBlank())
    }

    @Test fun dayparts() {
        assertEquals("Good morning", daypart(7))
        assertEquals("Good afternoon", daypart(13))
        assertEquals("Good evening", daypart(19))
        assertEquals("Burning the midnight oil", daypart(2))
    }

    @Test fun daypartHitGuardsLength() {
        assertNull(daypartHit("good morning what is the weather like today in mumbai city", "good morning what is the weather like today in mumbai city"))
        assertEquals("routine", daypartHit("good morning", "good morning")?.tool)
    }

    @Test fun fmtDurFormats() {
        assertEquals("1 min 30 sec", fmtDur(90))
        assertEquals("1 h", fmtDur(3600))
        assertEquals("45 sec", fmtDur(45))
    }
}

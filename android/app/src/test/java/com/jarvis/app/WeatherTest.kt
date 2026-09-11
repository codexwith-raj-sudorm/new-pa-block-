package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class WeatherTest {
    private val sample = """{"current_condition":[{"temp_C":"31","FeelsLikeC":"34","humidity":"62","windspeedKmph":"12","weatherDesc":[{"value":"Partly cloudy"}]}],"nearest_area":[{"areaName":[{"value":"Mumbai"}],"country":[{"value":"India"}]}]}"""

    @Test fun cityFromPhrases() {
        assertEquals("Pune", parseWeatherCity("weather in Pune"))
        assertEquals("Mumbai", parseWeatherCity("Mumbai weather"))
        assertEquals("Delhi", parseWeatherCity("forecast for Delhi tomorrow"))
        assertEquals("Pune", parseWeatherCity("will it rain in Pune?"))
    }

    @Test fun cityBlankWhenNone() {
        assertEquals("", parseWeatherCity("what's the weather"))
        assertEquals("", parseWeatherCity("weather"))
        assertEquals("", parseWeatherCity("how is the weather today"))
    }

    @Test fun wttrParses() {
        val w = parseWttr(sample)!!
        assertEquals("Mumbai", w.area)
        assertEquals("India", w.country)
        assertEquals("31", w.tempC)
        assertEquals("Partly cloudy", w.desc)
    }

    @Test fun wttrBadJsonNull() {
        assertNull(parseWttr("not json"))
        assertNull(parseWttr("{}"))
    }

    @Test fun formatsOneLiner() {
        val s = formatWeather(parseWttr(sample)!!)
        assertTrue(s.startsWith("Mumbai, India: Partly cloudy, 31"))
        assertTrue(s.contains("Humidity 62%"))
        assertTrue(s.contains("wind 12 km/h"))
    }
}

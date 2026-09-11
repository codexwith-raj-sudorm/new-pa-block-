package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTest {
    @Test fun openParse() {
        assertEquals(OpenApp("YouTube"), parseDeviceCommand("open YouTube"))
    }

    @Test fun openStripsAppSuffix() {
        assertEquals(OpenApp("whatsapp"), parseDeviceCommand("launch whatsapp app"))
    }

    @Test fun torchParse() {
        assertEquals(Torch(true), parseDeviceCommand("turn on the flashlight"))
        assertEquals(Torch(false), parseDeviceCommand("torch off"))
    }

    @Test fun callParse() {
        assertEquals(CallContact("mom"), parseDeviceCommand("call mom"))
        assertEquals(CallContact("+919876543210"), parseDeviceCommand("dial +919876543210"))
    }

    @Test fun wifiAndSettingsParse() {
        assertTrue(parseDeviceCommand("turn on wifi") is WifiPanel)
        assertTrue(parseDeviceCommand("open settings") is SysSettings)
    }

    @Test fun silenceParse() {
        assertTrue(parseDeviceCommand("silence my phone") is Silence)
        assertTrue(parseDeviceCommand("turn on silent mode") is Silence)
        assertTrue(parseDeviceCommand("turn off silent mode") is Unsilence)
        assertTrue(parseDeviceCommand("unsilence") is Unsilence)
        assertTrue(parseDeviceCommand("sound on") is Unsilence)
    }

    @Test fun alarmParse() {
        assertEquals(SetAlarm(7 to 0), parseDeviceCommand("wake me at 7"))
        assertEquals(SetAlarm(18 to 30), parseDeviceCommand("set an alarm for 6:30 pm"))
        assertEquals(SetAlarm(null), parseDeviceCommand("set an alarm"))
    }

    @Test fun alarmTimeEdges() {
        assertEquals(0 to 0, parseAlarmTime("12 am"))
        assertEquals(12 to 0, parseAlarmTime("12 pm"))
        assertEquals(19 to 5, parseAlarmTime("19:05"))
        assertNull(parseAlarmTime("no digits here"))
    }

    @Test fun timerParse() {
        assertEquals(SetTimer(300), parseDeviceCommand("set a timer for 5 minutes"))
        assertEquals(SetTimer(5400), parseDeviceCommand("countdown 1 hour 30 minutes"))
        assertEquals(SetTimer(60), parseDeviceCommand("timer"))
    }

    @Test fun durationEdges() {
        assertEquals(10, parseDuration("10 sec"))
        assertEquals(120, parseDuration("2m"))
        assertNull(parseDuration("no time words"))
    }

    @Test fun navSearchPlayParse() {
        assertEquals(NavigateTo("Andheri station"), parseDeviceCommand("navigate to Andheri station"))
        assertEquals(WebSearch("monsoon recipes"), parseDeviceCommand("search for monsoon recipes"))
        assertEquals(WebSearch("Taj Mahal"), parseDeviceCommand("google Taj Mahal"))
        assertEquals(PlayMedia("Believer"), parseDeviceCommand("play Believer"))
    }

    @Test fun garbageIsNull() {
        assertNull(parseDeviceCommand("hello there"))
        assertNull(parseDeviceCommand("what time is it"))
        assertNull(parseDeviceCommand("torch"))
    }
}

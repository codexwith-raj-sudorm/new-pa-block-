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

    @Test fun garbageIsNull() {
        assertNull(parseDeviceCommand("hello there"))
        assertNull(parseDeviceCommand("what time is it"))
        assertNull(parseDeviceCommand("torch"))
    }
}

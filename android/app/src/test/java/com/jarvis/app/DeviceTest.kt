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

    @Test fun textParse() {
        assertEquals(TextMessage(MsgApp.SMS, "mom", "I'll be late"), parseDeviceCommand("text mom I'll be late"))
        assertEquals(TextMessage(MsgApp.SMS, "ram", "hello"), parseDeviceCommand("send a text to ram hello"))
        assertEquals(TextMessage(MsgApp.SMS, "mom", "hi"), parseDeviceCommand("message mom hi"))
        assertEquals(TextMessage(MsgApp.WHATSAPP, "ram", "hi"), parseDeviceCommand("whatsapp ram hi"))
        assertEquals(TextMessage(MsgApp.WHATSAPP, "ram", "hi there"), parseDeviceCommand("send a whatsapp to ram hi there"))
        assertEquals(TextMessage(MsgApp.TELEGRAM, "", "launch at 6"), parseDeviceCommand("telegram launch at 6"))
        assertEquals(TextMessage(MsgApp.TELEGRAM, "ram", "hi"), parseDeviceCommand("telegram to ram hi"))
        assertEquals(TextMessage(MsgApp.SMS, "Mary Jane", "hello"), parseDeviceCommand("text \"Mary Jane\" hello"))
    }

    @Test fun openChatParse() {
        assertEquals(OpenChat(null, "mom"), parseDeviceCommand("open mom's chat"))
        assertEquals(OpenChat(MsgApp.WHATSAPP, "ram"), parseDeviceCommand("open my whatsapp chat with ram"))
        assertEquals(OpenChat(null, "ram"), parseDeviceCommand("open chat with ram"))
        assertEquals(OpenChat(MsgApp.WHATSAPP, "ram"), parseDeviceCommand("open ram's chat on whatsapp"))
    }

    @Test fun msgHelpers() {
        assertEquals(MsgApp.WHATSAPP, parseMsgApp("whatsapp"))
        assertEquals(null, parseMsgApp("signal"))
        assertEquals("919830012345", waDigits("+91 98300 12345", "IN"))
        assertEquals("919830012345", waDigits("9830012345", "IN"))
        assertEquals("9830012345", waDigits("9830012345", "US"))
    }

    @Test fun callControlParse() {
        assertEquals(AnswerCall, parseDeviceCommand("answer"))
        assertEquals(AnswerCall, parseDeviceCommand("answer the call"))
        assertEquals(AnswerCall, parseDeviceCommand("pick up the phone"))
        assertEquals(EndCall, parseDeviceCommand("hang up"))
        assertEquals(EndCall, parseDeviceCommand("end the call"))
        assertEquals(EndCall, parseDeviceCommand("reject call"))
        assertEquals(Speaker(true), parseDeviceCommand("speaker on"))
        assertEquals(Speaker(false), parseDeviceCommand("turn off the speaker"))
        assertEquals(Speaker(true), parseDeviceCommand("speakerphone on"))
        assertNull(parseDeviceCommand("answer my question"))
        assertEquals(OpenApp("speaker settings"), parseDeviceCommand("open speaker settings"))
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

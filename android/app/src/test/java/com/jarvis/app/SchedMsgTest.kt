package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchedMsgTest {
    @Test
    fun contactFirstParses() {
        val r = parseScheduledMessage("text mom I'll be late tomorrow at 9am")!!
        assertEquals(MsgApp.SMS, r.app)
        assertEquals("mom", r.contact)
        assertEquals("I'll be late", r.body)
        assertEquals(AtTime(9, 0, true), r.whenAt)
    }

    @Test
    fun toFormParses() {
        val r = parseScheduledMessage("send happy birthday to mom at 6pm")!!
        assertEquals(MsgApp.SMS, r.app)
        assertEquals("mom", r.contact)
        assertEquals("happy birthday", r.body)
        assertEquals(AtTime(18, 0, false), r.whenAt)
    }

    @Test
    fun whatsappInMinutesParses() {
        val r = parseScheduledMessage("whatsapp raj call me in 2 hours")!!
        assertEquals(MsgApp.WHATSAPP, r.app)
        assertEquals("raj", r.contact)
        assertEquals("call me", r.body)
        assertEquals(InMinutes(120), r.whenAt)
    }

    @Test
    fun noScheduleIsNull() {
        assertNull(parseScheduledMessage("text mom hello"))
        assertNull(parseScheduledMessage("send it"))
        assertNull(parseScheduledMessage("hello there"))
        assertNull(parseScheduledMessage("text mom party at christmas"))
    }

    @Test
    fun routerRoutesSched() {
        assertEquals("sched_msg", Router.detect("text mom hi tomorrow at 9am")?.tool)
        assertEquals("sched_msg", Router.detect("send the meeting date to raj at 5pm")?.tool)
        assertEquals("sched_list", Router.detect("my scheduled messages")?.tool)
        assertEquals("sched_cancel", Router.detect("cancel scheduled message 2")?.tool)
        assertEquals("2", Router.detect("cancel scheduled message 2")?.arg)
    }

    @Test
    fun schedDoesNotHijack() {
        assertEquals("device", Router.detect("text mom hello")?.tool)
        assertEquals("remind", Router.detect("remind me at 5pm gym")?.tool)
        assertEquals("time", Router.detect("what time is it")?.tool)
        assertEquals("joke", Router.detect("tell me a joke")?.tool)
    }
}

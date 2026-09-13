package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsToolsTest {
    @Test
    fun settingsFeaturesRouteToTools() {
        assertEquals("handsfree", Router.detect("hands-free on")?.tool)
        assertEquals("handsfree", Router.detect("turn handsfree off")?.tool)
        assertEquals("briefing", Router.detect("read my briefing")?.tool)
        assertEquals("dailybrief", Router.detect("daily briefing on")?.tool)
        assertEquals("hooks", Router.detect("open smart actions")?.tool)
        assertEquals("hooks", Router.detect("list smart actions")?.tool)
        assertEquals("miclang", Router.detect("listen in hindi")?.tool)
        assertEquals("miclang", Router.detect("mic auto")?.tool)
        assertEquals("reminders_ui", Router.detect("open reminders")?.tool)
        assertEquals("backup", Router.detect("back up my data")?.tool)
        assertEquals("battery", Router.detect("battery status")?.tool)
        assertEquals("battery", Router.detect("fix battery")?.tool)
        assertEquals("autostart", Router.detect("open autostart settings")?.tool)
        assertEquals("whatsnew", Router.detect("what's new")?.tool)
        assertEquals("help", Router.detect("what can you do?")?.tool)
        assertEquals("help", Router.detect("help")?.tool)
    }

    @Test
    fun noHijackOfNearbyPhrases() {
        // Smart-hook firing and Gemini paths must survive the new rules.
        assertNull(Router.detect("turn on bedroom light"))
        assertNull(Router.detect("do you understand english"))
        assertNull(Router.detect("help me write an essay"))
        assertNull(Router.detect("goodnight moon"))
    }

    @Test
    fun existingToolsPreserved() {
        assertEquals("reminders", Router.detect("reminders")?.tool)
        assertEquals("calc", Router.detect("what is 2+2")?.tool)
        assertEquals("joke", Router.detect("tell me a joke")?.tool)
        assertEquals("device", Router.detect("turn on the flashlight")?.tool)
        assertEquals("routine", Router.detect("good morning")?.tool)
    }
}

package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class HookTest {
    private val hooks = listOf(
        HookAction("bedroom light", "http://x/on", "GET"),
        HookAction("light", "http://x/l", "GET")
    )

    @Test fun matchesLongest() {
        assertEquals("bedroom light", matchHook("turn on the bedroom light", hooks)?.name)
    }

    @Test fun matchesShort() {
        assertEquals("light", matchHook("light off", hooks)?.name)
    }

    @Test fun noMatch() {
        assertNull(matchHook("what time is it", hooks))
        assertNull(matchHook("turn on the kitchen fan", hooks))
    }
}

package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    @Test fun filtersNewerOnlyNewestFirst() {
        val all = whatsNew(0)
        assertEquals(CHANGELOG.size, all.size)
        assertEquals(all.map { it.code }.sortedDescending(), all.map { it.code })
        val recent = whatsNew(10)
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.all { it.code > 10 })
        assertEquals(CHANGELOG.first().code, recent.first().code)
    }

    @Test fun emptyWhenUpToDate() {
        assertTrue(whatsNew(CHANGELOG.first().code).isEmpty())
        assertTrue(whatsNew(999).isEmpty())
    }
}

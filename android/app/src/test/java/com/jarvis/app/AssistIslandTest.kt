package com.jarvis.app

import com.jarvis.app.frontend.design.edgeFlashAlpha
import com.jarvis.app.frontend.design.flowBorderAlpha
import com.jarvis.app.frontend.design.islandDragCloses
import com.jarvis.app.frontend.design.listeningDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistIslandTest {
    @Test
    fun dotsCycleFourSteps() {
        assertEquals("", listeningDots(0))
        assertEquals(".", listeningDots(1))
        assertEquals("..", listeningDots(2))
        assertEquals("...", listeningDots(3))
        assertEquals("", listeningDots(4))
        assertEquals("...", listeningDots(-1))
    }

    @Test
    fun dragNeeds40pxDown() {
        assertFalse(islandDragCloses(0f))
        assertFalse(islandDragCloses(40f))
        assertFalse(islandDragCloses(-100f))
        assertTrue(islandDragCloses(40.1f))
    }

    @Test
    fun edgeFlashEnvelopePeaksEarly() {
        assertEquals(0f, edgeFlashAlpha(0f), 1e-6f)
        assertEquals(1f, edgeFlashAlpha(0.15f), 1e-6f)
        assertEquals(0.35f, edgeFlashAlpha(0.5f), 1e-6f)
        assertEquals(0f, edgeFlashAlpha(1f), 1e-6f)
        assertEquals(0f, edgeFlashAlpha(-1f), 1e-6f)
        assertEquals(0f, edgeFlashAlpha(2f), 1e-6f)
    }

    @Test
    fun flowBorderHoldsThenFades() {
        assertEquals(0f, flowBorderAlpha(0f), 1e-6f)
        assertEquals(0.5f, flowBorderAlpha(0.05f), 1e-6f)
        assertEquals(1f, flowBorderAlpha(0.1f), 1e-6f)
        assertEquals(1f, flowBorderAlpha(0.5f), 1e-6f)
        assertEquals(0.5f, flowBorderAlpha(0.9f), 1e-6f)
        assertEquals(0f, flowBorderAlpha(1f), 1e-6f)
    }
}

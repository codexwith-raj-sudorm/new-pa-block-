package com.jarvis.app

import com.jarvis.app.backend.gesture.ExponentialPointSmoother
import com.jarvis.app.backend.gesture.GestureDebouncer
import com.jarvis.app.backend.gesture.GestureEvent
import com.jarvis.app.backend.gesture.ModelDownloadProgress
import com.jarvis.app.backend.gesture.NormalizedPoint
import com.jarvis.app.backend.gesture.mapToScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {
    @Test
    fun frontCameraMappingMirrorsXAndClamps() {
        assertEquals(80f, mapToScreen(NormalizedPoint(0.2f, 0.5f), 100f, 200f).x, 0.001f)
        assertEquals(0f, mapToScreen(NormalizedPoint(2f, -1f), 100f, 200f).x, 0.001f)
        assertEquals(0f, mapToScreen(NormalizedPoint(2f, -1f), 100f, 200f).y, 0.001f)
    }

    @Test
    fun marginsKeepPointerAwayFromEdges() {
        val point = mapToScreen(NormalizedPoint(0f, 0f), 100f, 100f, mirrorX = false, marginFraction = 0.1f)
        assertEquals(10f, point.x, 0.001f)
        assertEquals(10f, point.y, 0.001f)
    }

    @Test
    fun smootherStartsAtCurrentPointAndBlendsNext() {
        val smoother = ExponentialPointSmoother(alpha = 0.5f)
        assertEquals(NormalizedPoint(0f, 0f), smoother.update(NormalizedPoint(0f, 0f)))
        assertEquals(NormalizedPoint(0.5f, 0.5f), smoother.update(NormalizedPoint(1f, 1f)))
    }

    @Test
    fun fistRequiresStableFramesAndEmitsOnceUntilNeutral() {
        val debouncer = GestureDebouncer(stableFrames = 3, cooldownMs = 0)
        assertNull(debouncer.observe("Closed_Fist", 0.9f, 1L))
        assertNull(debouncer.observe("Closed_Fist", 0.9f, 2L))
        assertTrue(debouncer.observe("Closed_Fist", 0.9f, 3L) is GestureEvent.Click)
        assertNull(debouncer.observe("Closed_Fist", 0.9f, 4L))
        assertNull(debouncer.observe(null, 0f, 5L))
        assertTrue(debouncer.observe("Closed_Fist", 0.9f, 6L) == null)
    }

    @Test
    fun modelProgressReportsDownloadFraction() {
        val progress = ModelDownloadProgress(5L * 1024L, 10L * 1024L)
        assertEquals(0.5f, progress.fraction ?: -1f, 0.001f)
    }

    @Test
    fun unknownModelSizeHasNoFraction() {
        assertNull(ModelDownloadProgress(1024L, null).fraction)
    }

    @Test
    fun lowConfidenceDoesNotTrigger() {
        val debouncer = GestureDebouncer(stableFrames = 1, cooldownMs = 0)
        assertNull(debouncer.observe("Closed_Fist", 0.77f, 1L))
        assertNull(debouncer.observe("Thumb_Up", 0.5f, 2L))
    }
}

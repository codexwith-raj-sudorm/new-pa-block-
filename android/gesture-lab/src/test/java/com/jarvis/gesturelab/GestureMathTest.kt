package com.jarvis.gesturelab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureMathTest {
    @Test fun mirrorKeepsHandDirectionIntuitive() {
        assertEquals(0.8f, mapToPreview(NormalizedPoint(0.2f, 0.4f), 100f, 100f).x, 0.001f)
        assertEquals(0.2f, mapToPreview(NormalizedPoint(0.2f, 0.4f), 100f, 100f, false).x, 0.001f)
    }

    @Test fun smootherBlendsWithoutOvershoot() {
        val smoother = ExponentialPointSmoother(0.5f)
        assertEquals(NormalizedPoint(0f, 0f), smoother.update(NormalizedPoint(0f, 0f)))
        assertEquals(NormalizedPoint(0.5f, 0.5f), smoother.update(NormalizedPoint(1f, 1f)))
    }

    @Test fun dataSizeAndUnknownProgressAreSafe() {
        assertEquals("1.0 MB", formatDataSize(1024L * 1024L))
        assertNull(ModelDownloadProgress(1024L, null).fraction)
        assertEquals(0.5f, ModelDownloadProgress(5L, 10L).fraction ?: -1f, 0.001f)
    }
}

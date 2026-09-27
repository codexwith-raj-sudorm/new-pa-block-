package com.jarvis.app

import com.jarvis.app.ui.components.fibSphere
import com.jarvis.app.ui.components.fmtFreq
import com.jarvis.app.ui.components.fmtPingTag
import com.jarvis.app.ui.components.holoRingPoints
import com.jarvis.app.ui.components.holoSpinMs
import com.jarvis.app.ui.components.modelTag
import com.jarvis.app.ui.components.projectScale
import com.jarvis.app.ui.components.rotX
import com.jarvis.app.ui.components.rotY
import com.jarvis.app.ui.components.sphereDepth
import com.jarvis.app.ui.components.sphereNeighbors
import com.jarvis.app.ui.components.voiceTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class NeuralHologramTest {
    @Test fun fibPointsLieOnUnitSphere() {
        for (i in 0 until 90) {
            val (x, y, z) = fibSphere(i, 90)
            val r = sqrt(x * x + y * y + z * z)
            assertTrue("r=$r", abs(r - 1f) < 0.001f)
        }
    }

    @Test fun rotationsPreserveRadius() {
        val p = fibSphere(7, 90)
        val q = rotX(rotY(p, 123f), 22f)
        val r = sqrt(q.first * q.first + q.second * q.second + q.third * q.third)
        assertTrue("r=$r", abs(r - 1f) < 0.001f)
    }

    @Test fun rotYQuarterTurnSwapsAxes() {
        val (x, _, z) = rotY(Triple(1f, 0f, 0f), 90f)
        assertTrue(abs(x) < 0.001f)
        assertTrue(abs(z + 1f) < 0.001f)
    }

    @Test fun projectionGrowsTowardViewer() {
        assertTrue(projectScale(0.9f) > projectScale(0f))
        assertTrue(projectScale(0f) > projectScale(-0.9f))
        assertEquals(1f, projectScale(0f), 0.001f)
    }

    @Test fun depthMapsRearToFront() {
        assertEquals(0f, sphereDepth(-1f), 0.001f)
        assertEquals(0.5f, sphereDepth(0f), 0.001f)
        assertEquals(1f, sphereDepth(1f), 0.001f)
    }

    @Test fun neighborsHaveNoSelfLoops() {
        val pairs = sphereNeighbors(90)
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { (a, b) -> a != b })
        assertTrue(pairs.size == pairs.toSet().size)
    }

    @Test fun spinMatchesDesignSpec() {
        assertEquals(7000, holoSpinMs(true, false, false))
        assertEquals(2500, holoSpinMs(false, true, false))
        assertEquals(4500, holoSpinMs(false, false, true))
        assertEquals(7000, holoSpinMs(false, false, false))
    }

    @Test fun ringPointsAreFiniteAndTilted() {
        val pts = holoRingPoints(100f, 32f, 0f)
        assertEquals(72, pts.size)
        assertTrue(pts.all { (x, y) -> x.isFinite() && y.isFinite() })
        assertEquals(100f, pts[0].first, 0.01f)
        assertEquals(0f, pts[0].second, 0.01f)
        val tilted = holoRingPoints(100f, 32f, 90f)
        assertEquals(0f, tilted[0].first, 0.01f)
        assertEquals(100f, tilted[0].second, 0.01f)
    }

    @Test fun telemetryFormats() {
        assertTrue(fmtFreq(0f).startsWith("FREQ: "))
        assertTrue(fmtFreq(1f).endsWith(" Hz"))
        assertEquals("42MS", fmtPingTag("42 ms"))
        assertEquals("—", fmtPingTag(""))
        assertEquals("GEMINI-2.5-FLASH", modelTag("models/gemini-2.5-flash"))
        assertEquals("VOICE • EN", voiceTag(false))
        assertEquals("VOICE • HI", voiceTag(true))
    }
}

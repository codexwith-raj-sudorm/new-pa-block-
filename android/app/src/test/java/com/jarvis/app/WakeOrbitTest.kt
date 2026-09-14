package com.jarvis.app

import com.jarvis.app.ui.components.brainEdges
import com.jarvis.app.ui.components.focusScale
import com.jarvis.app.ui.components.brainNode
import com.jarvis.app.ui.components.orbitDepth
import com.jarvis.app.ui.components.orbitXY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeOrbitTest {
    @Test
    fun nodesStayInUnitCircle() {
        val n = 14
        assertEquals(0f to 0f, brainNode(0, n))
        for (i in 0 until n) {
            val (x, y) = brainNode(i, n)
            assertTrue(x * x + y * y <= 1f)
            assertEquals(brainNode(i, n), brainNode(i, n))
        }
        assertEquals(0f to 0f, brainNode(0, 1))
    }

    @Test
    fun edgesAreValid() {
        val n = 14
        val edges = brainEdges(n)
        assertEquals(n + n / 2, edges.size)
        for ((a, b) in edges) {
            assertTrue(a in 0 until n)
            assertTrue(b in 0 until n)
            assertTrue(a != b)
        }
        assertTrue(brainEdges(2).isEmpty())
    }

    @Test
    fun orbitCardinals() {
        val (x0, y0) = orbitXY(0f, 10f, 20f)
        assertEquals(10f, x0, 0.001f)
        assertEquals(0f, y0, 0.001f)
        val (x90, y90) = orbitXY(90f, 10f, 20f)
        assertEquals(0f, x90, 0.001f)
        assertEquals(20f, y90, 0.001f)
        val (x180, y180) = orbitXY(180f, 10f, 20f)
        assertEquals(-10f, x180, 0.001f)
        assertEquals(0f, y180, 0.001f)
    }

    @Test
    fun focusBoostsScale() {
        assertTrue(focusScale(0.5f, true) > focusScale(0.5f, false))
        assertEquals(0.82f + 0.18f * 0.5f, focusScale(0.5f, false), 0.001f)
    }

    @Test
    fun depthFrontBack() {
        assertEquals(1f, orbitDepth(90f), 0.001f)
        assertEquals(0f, orbitDepth(270f), 0.001f)
        assertEquals(0.5f, orbitDepth(0f), 0.001f)
    }
}

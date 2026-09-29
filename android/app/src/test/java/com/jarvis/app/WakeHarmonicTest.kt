package com.jarvis.app

import com.jarvis.app.frontend.design.HubState
import com.jarvis.app.frontend.design.hubLabelFor
import com.jarvis.app.frontend.design.hubPeriodMs
import com.jarvis.app.frontend.design.hubPulse
import com.jarvis.app.frontend.design.hubSpinDir
import com.jarvis.app.frontend.design.hubSpinMs
import com.jarvis.app.frontend.design.hubStateFor
import com.jarvis.app.frontend.design.rippleAlpha
import com.jarvis.app.frontend.design.ripplePhase
import com.jarvis.app.frontend.design.rippleScale
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeHarmonicTest {
    @Test
    fun ripplesStaggerByThirds() {
        assertEquals(0f, ripplePhase(0f, 0), 1e-6f)
        assertEquals(1f / 3f, ripplePhase(0f, 1), 1e-6f)
        assertEquals(2f / 3f, ripplePhase(0f, 2), 1e-6f)
        assertEquals(0.2333f, ripplePhase(0.9f, 1), 1e-3f)
    }

    @Test
    fun rippleGrowsAndFades() {
        assertEquals(0.8f, rippleScale(0f), 1e-6f)
        assertEquals(2.8f, rippleScale(1f), 1e-6f)
        assertEquals(0.8f, rippleAlpha(0f), 1e-6f)
        assertEquals(0f, rippleAlpha(1f), 1e-6f)
        assertEquals(0.4f, rippleAlpha(0.5f), 1e-6f)
    }

    @Test
    fun hubStatePrefersLiveAudio() {
        assertEquals(HubState.LISTENING, hubStateFor(true, false, false))
        assertEquals(HubState.LISTENING, hubStateFor(false, true, false))
        assertEquals(HubState.LISTENING, hubStateFor(true, false, true))
        assertEquals(HubState.WAKE, hubStateFor(false, false, true))
        assertEquals(HubState.IDLE, hubStateFor(false, false, false))
    }

    @Test
    fun hubLabelNamesTheAudio() {
        assertEquals("[MIC: LIVE]", hubLabelFor(true, false, false))
        assertEquals("[MIC: LIVE]", hubLabelFor(true, true, true))
        assertEquals("[VOICE: LIVE]", hubLabelFor(false, true, false))
        assertEquals("[WAKE: RECOGNIZED]", hubLabelFor(false, false, true))
        assertEquals("[SYS: IDLE]", hubLabelFor(false, false, false))
    }

    @Test
    fun hubSpinAndPeriodPerState() {
        assertEquals(15000, hubSpinMs(HubState.IDLE))
        assertEquals(4000, hubSpinMs(HubState.WAKE))
        assertEquals(8000, hubSpinMs(HubState.LISTENING))
        assertEquals(1f, hubSpinDir(HubState.IDLE), 0f)
        assertEquals(1f, hubSpinDir(HubState.WAKE), 0f)
        assertEquals(-1f, hubSpinDir(HubState.LISTENING), 0f)
        assertEquals(3000, hubPeriodMs(HubState.IDLE))
        assertEquals(300, hubPeriodMs(HubState.WAKE))
        assertEquals(1000, hubPeriodMs(HubState.LISTENING))
    }

    @Test
    fun hubPulseBreathesAndStrobes() {
        val idle0 = hubPulse(HubState.IDLE, 0f)
        assertEquals(0.9f, idle0.first, 1e-6f)
        assertEquals(0.7f, idle0.second, 1e-6f)
        val idleMid = hubPulse(HubState.IDLE, 0.5f)
        assertEquals(1.1f, idleMid.first, 1e-6f)
        assertEquals(1f, idleMid.second, 1e-6f)
        val wakeMid = hubPulse(HubState.WAKE, 0.5f)
        assertEquals(1.3f, wakeMid.first, 1e-5f)
        assertEquals(1f, wakeMid.second, 1e-6f)
        val live0 = hubPulse(HubState.LISTENING, 0f)
        assertEquals(0.8f, live0.first, 1e-6f)
        assertEquals(1f, live0.second, 0f)
        assertEquals(1.1f, hubPulse(HubState.LISTENING, 0.7f).first, 1e-5f)
        assertEquals(0.8f, hubPulse(HubState.LISTENING, 1f).first, 1e-5f)
    }
}

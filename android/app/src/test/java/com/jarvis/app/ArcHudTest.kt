package com.jarvis.app

import com.jarvis.app.frontend.design.arcSpinMs
import com.jarvis.app.frontend.design.coreStateLabel
import com.jarvis.app.frontend.design.fmtPingTag
import com.jarvis.app.frontend.design.hudStatusLine
import com.jarvis.app.frontend.design.voiceTag
import org.junit.Assert.assertEquals
import org.junit.Test

class ArcHudTest {
    @Test
    fun statusLineCoversCombos() {
        assertEquals("NEURAL LINK ACTIVE", hudStatusLine(true, false))
        assertEquals("NEURAL LINK ACTIVE • WAKE ARMED", hudStatusLine(true, true))
        assertEquals("OFFLINE", hudStatusLine(false, false))
        assertEquals("OFFLINE • WAKE ARMED", hudStatusLine(false, true))
    }

    @Test
    fun stateLabelPriority() {
        assertEquals("STANDBY", coreStateLabel(false, false, false))
        assertEquals("THINKING", coreStateLabel(false, true, false))
        assertEquals("LISTENING", coreStateLabel(true, true, false))
        assertEquals("SPEAKING", coreStateLabel(true, true, true))
        assertEquals("CONVO LIVE", coreStateLabel(false, false, false, true))
        assertEquals("LISTENING", coreStateLabel(true, false, false, true))
    }

    @Test
    fun spinSpeeds() {
        assertEquals(1200, arcSpinMs(true, false))
        assertEquals(2600, arcSpinMs(false, true))
        assertEquals(9000, arcSpinMs(false, false))
        assertEquals(1200, arcSpinMs(true, true))
    }

    @Test
    fun pingAndVoiceTags() {
        assertEquals("42MS", fmtPingTag("42 ms"))
        assertEquals("—", fmtPingTag(""))
        assertEquals("VOICE • EN", voiceTag(false))
        assertEquals("VOICE • HI", voiceTag(true))
    }
}

package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoicePrintTest {

    /** Frequency-swept tone: time-varying like real speech (pure sines collapse under CMN). */
    private fun chirpPcm(f0: Double, f1: Double, seconds: Double, amp: Double = 9000.0): ShortArray {
        val n = (seconds * VP_SAMPLE_RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / VP_SAMPLE_RATE
            val phase = 2 * Math.PI * (f0 * t + (f1 - f0) * t * t / (2 * seconds))
            (amp * sin(phase)).toInt().toShort()
        }
    }

    private fun sinePcm(freqHz: Double, seconds: Double, amp: Double = 9000.0): ShortArray {
        val n = (seconds * VP_SAMPLE_RATE).toInt()
        return ShortArray(n) { i -> (amp * sin(2 * Math.PI * freqHz * i / VP_SAMPLE_RATE)).toInt().toShort() }
    }

    private fun noisy(base: ShortArray): ShortArray {
        var seed = 12345L
        return ShortArray(base.size) { i ->
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val noise = (seed % 1000) - 500
            (base[i] + noise).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    @Test
    fun dtwIdentityIsZero() {
        val mf = mfccFrames(chirpPcm(200.0, 500.0, 1.5))
        assertTrue(mf.size > 50)
        assertEquals(0.0, dtwDistance(mf, mf), 0.0)
    }

    @Test
    fun mfccSeparatesDifferentPitches() {
        val a = mfccFrames(chirpPcm(200.0, 400.0, 1.5))
        val b = mfccFrames(chirpPcm(700.0, 1000.0, 1.5))
        val same = dtwDistance(a, mfccFrames(noisy(chirpPcm(200.0, 400.0, 1.5))))
        val cross = dtwDistance(a, b)
        assertTrue("cross=$cross same=$same", cross > 1.0 && same < cross)
    }

    @Test
    fun trimKeepsSpeechDropsSilence() {
        val pre = ShortArray(8000)
        val mid = sinePcm(300.0, 1.0)
        val post = ShortArray(8000)
        val full = pre + mid + post
        val t = trimSilencePcm(full)
        assertTrue(t.size < full.size)
        assertTrue("trimmed=${t.size}", t.size > VP_SAMPLE_RATE * 8 / 10)
        assertEquals(0, trimSilencePcm(ShortArray(8000)).size)
    }

    @Test
    fun templatesRoundTrip() {
        val t = List(3) { mfccFrames(sinePcm(250.0 + it * 10, 1.2)) }
        val s = templatesToString(t)
        assertTrue(s.isNotBlank())
        val back = templatesFromString(s)
        assertNotNull(back)
        assertEquals(3, back!!.size)
        assertEquals(t[0].size, back[0].size)
        assertEquals(t[1][5][7], back[1][5][7], 0f)
        assertNull(templatesFromString("not-base64!!!"))
        assertNull(templatesFromString(""))
    }

    @Test
    fun thresholdSelfCalibrates() {
        assertEquals(1.8, vpThresholdFor(1.0f, 1.8f), 1e-6)
        assertEquals(0.35, vpThresholdFor(0.01f, 1.8f), 1e-9)
        assertEquals(2.5, vpThresholdFor(3.0f, 2.4f), 1e-9)
    }

    @Test
    fun verifyEndToEndSynthetic() {
        val templates = List(3) { mfccFrames(chirpPcm(200.0, 400.0, 1.5)) }
        val spread = maxOf(
            dtwDistance(templates[0], templates[1]),
            dtwDistance(templates[0], templates[2]),
            dtwDistance(templates[1], templates[2])
        )
        val thr = vpThresholdFor(spread.toFloat(), 1.8f)
        assertEquals(VpVerdict.MATCH, verifyVoiceprint(chirpPcm(200.0, 400.0, 1.5), templates, thr))
        assertEquals(VpVerdict.MISMATCH, verifyVoiceprint(chirpPcm(700.0, 1000.0, 1.5), templates, thr))
        assertEquals(VpVerdict.UNKNOWN, verifyVoiceprint(ShortArray(4000), templates, thr))
        assertEquals(VpVerdict.UNKNOWN, verifyVoiceprint(chirpPcm(200.0, 400.0, 1.5), emptyList(), thr))
    }

    @Test
    fun gateDecisionMatrix() {
        // Guard off: everything passes.
        var d = voiceGateDecision("open camera", false, true, "Raj", true, VpVerdict.MISMATCH)
        assertTrue(d.send && d.cleaned == "open camera")
        // Unenrolled + locked: legacy name rule.
        d = voiceGateDecision("open camera", true, true, "Raj", false, VpVerdict.UNKNOWN)
        assertTrue(!d.send)
        d = voiceGateDecision("Raj open camera", true, true, "Raj", false, VpVerdict.UNKNOWN)
        assertTrue(d.send && d.cleaned == "open camera" && d.bypassGuard)
        // Unenrolled + unlocked: open.
        d = voiceGateDecision("open camera", true, false, "Raj", false, VpVerdict.UNKNOWN)
        assertTrue(d.send)
        // Enrolled + match: passes raw even locked.
        d = voiceGateDecision("open camera", true, true, "Raj", true, VpVerdict.MATCH)
        assertTrue(d.send && d.cleaned == "open camera" && d.bypassGuard)
        // Enrolled + mismatch: name required even unlocked.
        d = voiceGateDecision("open camera", true, false, "Raj", true, VpVerdict.MISMATCH)
        assertTrue(!d.send)
        d = voiceGateDecision("Raj open camera", true, false, "Raj", true, VpVerdict.MISMATCH)
        assertTrue(d.send && d.cleaned == "open camera" && d.bypassGuard)
        // Mismatch + blank name: fail closed with typed fallback.
        d = voiceGateDecision("open camera", true, false, "", true, VpVerdict.MISMATCH)
        assertTrue(!d.send && d.note!!.contains("type"))
        // Enrolled + unknown capture: legacy path.
        d = voiceGateDecision("open camera", true, true, "Raj", true, VpVerdict.UNKNOWN)
        assertTrue(!d.send)
    }
}

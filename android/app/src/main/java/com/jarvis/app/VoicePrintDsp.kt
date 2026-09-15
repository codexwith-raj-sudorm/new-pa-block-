package com.jarvis.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Text-dependent voiceprint: MFCC + DTW on a fixed enrollment phrase, 100% local.
 * No model file, no dependency, no cloud. JVM-pure (uses java.util.Base64, present
 * on Android 26+) so the whole pipeline is unit-tested (see VoicePrintTest).
 *
 * Honest limits: this keeps out casual strangers, NOT recordings of the owner
 * (no liveness detection) or a deliberate mimic of a short phrase. The Master
 * voice guard treats MISMATCH as "name required" and UNKNOWN as "legacy path",
 * so the owner always has a fallback and is never locked out.
 */
const val VP_SAMPLE_RATE = 16000
const val VP_FRAME_MS = 25
const val VP_HOP_MS = 10
const val VP_FFT_SIZE = 512
const val VP_MEL_BANDS = 26
const val VP_CEPS = 13

/** Enrollment aborts when the 3 takes differ more than this (user was inconsistent). */
const val VP_ENROLL_MAX_SPREAD = 3.0

enum class VpVerdict { MATCH, MISMATCH, UNKNOWN }

/** In-place radix-2 FFT on [re]/[im] (size must be a power of two). */
fun fftInPlace(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j and bit.inv()
            bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * Math.PI / len
        val wRe = cos(ang)
        val wIm = sin(ang)
        var i = 0
        while (i < n) {
            var cRe = 1.0
            var cIm = 0.0
            for (k in 0 until len / 2) {
                val uRe = re[i + k].toDouble()
                val uIm = im[i + k].toDouble()
                val vRe = re[i + k + len / 2] * cRe - im[i + k + len / 2] * cIm
                val vIm = re[i + k + len / 2] * cIm + im[i + k + len / 2] * cRe
                re[i + k] = (uRe + vRe).toFloat()
                im[i + k] = (uIm + vIm).toFloat()
                re[i + k + len / 2] = (uRe - vRe).toFloat()
                im[i + k + len / 2] = (uIm - vIm).toFloat()
                val nRe = cRe * wRe - cIm * wIm
                cIm = cRe * wIm + cIm * wRe
                cRe = nRe
            }
            i += len
        }
        len *= 2
    }
}

private fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
private fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

/** Triangular mel filterbank: [bands] filters over [fftSize]/2+1 spectrum bins. */
fun melFilterbank(bands: Int, fftSize: Int, sampleRate: Int): List<FloatArray> {
    val bins = fftSize / 2 + 1
    val lowMel = hzToMel(0.0)
    val highMel = hzToMel(sampleRate / 2.0)
    val points = DoubleArray(bands + 2) { i ->
        melToHz(lowMel + (highMel - lowMel) * i / (bands + 1)) / sampleRate * fftSize
    }
    return List(bands) { m ->
        FloatArray(bins) { b ->
            val f = b.toDouble()
            when {
                f < points[m] || f > points[m + 2] -> 0f
                f <= points[m + 1] -> ((f - points[m]) / (points[m + 1] - points[m])).toFloat()
                else -> ((points[m + 2] - f) / (points[m + 2] - points[m + 1])).toFloat()
            }
        }
    }
}

/** MFCC frames (13 cepstra + utterance CMN) for 16-bit mono PCM. Pure. */
fun mfccFrames(pcm: ShortArray, sampleRate: Int = VP_SAMPLE_RATE): List<FloatArray> {
    val frameLen = sampleRate * VP_FRAME_MS / 1000
    val hop = sampleRate * VP_HOP_MS / 1000
    if (pcm.size < frameLen) return emptyList()
    val emp = FloatArray(pcm.size) { i ->
        pcm[i] / 32768f - (if (i > 0) 0.97f * pcm[i - 1] / 32768f else 0f)
    }
    val ham = FloatArray(frameLen) { i ->
        (0.54 - 0.46 * cos(2.0 * Math.PI * i / (frameLen - 1))).toFloat()
    }
    val filters = melFilterbank(VP_MEL_BANDS, VP_FFT_SIZE, sampleRate)
    val bins = VP_FFT_SIZE / 2 + 1
    val out = mutableListOf<FloatArray>()
    var start = 0
    val re = FloatArray(VP_FFT_SIZE)
    val im = FloatArray(VP_FFT_SIZE)
    while (start + frameLen <= emp.size) {
        for (i in 0 until VP_FFT_SIZE) {
            re[i] = 0f
            im[i] = 0f
        }
        for (i in 0 until frameLen) re[i] = emp[start + i] * ham[i]
        fftInPlace(re, im)
        val logE = FloatArray(VP_MEL_BANDS)
        for (m in 0 until VP_MEL_BANDS) {
            var e = 0.0
            val f = filters[m]
            for (b in 0 until bins) {
                val w = f[b]
                if (w > 0f) {
                    val p = (re[b] * re[b] + im[b] * im[b]).toDouble()
                    e += p * w
                }
            }
            logE[m] = ln(max(e, 1e-10)).toFloat()
        }
        val ceps = FloatArray(VP_CEPS)
        for (k in 0 until VP_CEPS) {
            var s = 0.0
            for (m in 0 until VP_MEL_BANDS) s += logE[m] * cos(Math.PI * k * (m + 0.5) / VP_MEL_BANDS)
            ceps[k] = s.toFloat()
        }
        out.add(ceps)
        start += hop
    }
    if (out.isNotEmpty()) {
        for (k in 0 until VP_CEPS) {
            var mean = 0.0
            for (f in out) mean += f[k]
            mean /= out.size
            for (f in out) f[k] = (f[k] - mean).toFloat()
        }
    }
    return out
}

/** Energy-gate trim (10ms hops, 100ms padding). Returns the speech slice. Pure. */
fun trimSilencePcm(pcm: ShortArray, hop: Int = VP_SAMPLE_RATE / 100): ShortArray {
    if (pcm.isEmpty()) return pcm
    val frames = (pcm.size + hop - 1) / hop
    val energy = DoubleArray(frames) { f ->
        val s = f * hop
        val e = min(s + hop, pcm.size)
        var sum = 0.0
        for (i in s until e) sum += abs(pcm[i].toInt())
        sum / (e - s)
    }
    val peak = energy.maxOrNull() ?: 0.0
    if (peak <= 0.0) return ShortArray(0)
    val thr = max(peak * 0.03, 250.0)
    val first = energy.indexOfFirst { it >= thr }
    val last = energy.indexOfLast { it >= thr }
    if (first < 0) return ShortArray(0)
    val from = max(0, first - 10) * hop
    val to = min(pcm.size, (min(frames - 1, last + 10) + 1) * hop)
    return pcm.sliceArray(from until to)
}

private fun euclid(x: FloatArray, y: FloatArray): Double {
    var s = 0.0
    for (i in x.indices) {
        val d = (x[i] - y[i]).toDouble()
        s += d * d
    }
    return sqrt(s)
}

/**
 * Length-normalized DTW distance with a reachability-safe band. Pure.
 * Identical sequences score exactly 0.
 */
fun dtwDistance(a: List<FloatArray>, b: List<FloatArray>): Double {
    if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
    val n = a.size
    val m = b.size
    val w = abs(n - m) + max(n, m) / 5 + 10
    val inf = 1e100
    var prevC = DoubleArray(m + 1) { inf }
    var prevL = IntArray(m + 1)
    var curC = DoubleArray(m + 1)
    var curL = IntArray(m + 1)
    prevC[0] = 0.0
    for (i in 1..n) {
        curC.fill(inf)
        curL.fill(0)
        val j0 = max(1, i - w)
        val j1 = min(m, i + w)
        for (j in j0..j1) {
            val d = euclid(a[i - 1], b[j - 1])
            var best = inf
            var bl = 0
            if (prevC[j - 1] < inf) {
                val c = (prevC[j - 1] + d) / (prevL[j - 1] + 1)
                if (c < best) {
                    best = c
                    bl = prevL[j - 1] + 1
                }
            }
            if (prevC[j] < inf) {
                val c = (prevC[j] + d) / (prevL[j] + 1)
                if (c < best) {
                    best = c
                    bl = prevL[j] + 1
                }
            }
            if (curC[j - 1] < inf) {
                val c = (curC[j - 1] + d) / (curL[j - 1] + 1)
                if (c < best) {
                    best = c
                    bl = curL[j - 1] + 1
                }
            }
            if (best < inf) {
                curC[j] = best * bl
                curL[j] = bl
            }
        }
        val tc = prevC
        prevC = curC
        curC = tc
        val tl = prevL
        prevL = curL
        curL = tl
    }
    if (prevC[m] >= inf) return Double.MAX_VALUE
    return prevC[m] / max(1, prevL[m])
}

/** Trim + MFCC for one enrollment/verify take. Null when too little speech. Pure. */
fun mfccOfTake(pcm: ShortArray): List<FloatArray>? {
    val t = trimSilencePcm(pcm)
    if (t.size < VP_SAMPLE_RATE * 6 / 10) return null
    val mf = mfccFrames(t)
    return if (mf.size >= 50) mf else null
}

/** Serialize templates (count + frames + floats) to Base64. Pure. */
fun templatesToString(t: List<List<FloatArray>>): String {
    var n = 4
    for (tm in t) n += 4 + tm.size * VP_CEPS * 4
    val buf = java.nio.ByteBuffer.allocate(n)
    buf.putInt(t.size)
    for (tm in t) {
        buf.putInt(tm.size)
        for (f in tm) for (v in f) buf.putFloat(v)
    }
    return java.util.Base64.getEncoder().encodeToString(buf.array())
}

/** Parse [templatesToString] output. Null on any corruption. Pure. */
fun templatesFromString(s: String): List<List<FloatArray>>? {
    try {
        if (s.isBlank()) return null
        val buf = java.nio.ByteBuffer.wrap(java.util.Base64.getDecoder().decode(s.trim()))
        if (buf.remaining() < 4) return null
        val count = buf.int
        if (count !in 1..5) return null
        val out = mutableListOf<List<FloatArray>>()
        repeat(count) {
            if (buf.remaining() < 4) return null
            val frames = buf.int
            if (frames !in 20..800) return null
            if (buf.remaining() < frames * VP_CEPS * 4) return null
            out.add(List(frames) { FloatArray(VP_CEPS) { buf.float } })
        }
        if (buf.remaining() != 0) return null
        return out
    } catch (_: Exception) {
        return null
    }
}

/** Self-calibrated threshold: enrollment spread x sensitivity multiplier, clamped. Pure. */
fun vpThresholdFor(base: Float, mult: Float): Double =
    (base.toDouble() * mult.toDouble()).coerceIn(0.35, 2.5)

/** Full verification of captured PCM against templates. Pure. */
fun verifyVoiceprint(pcm: ShortArray, templates: List<List<FloatArray>>, threshold: Double): VpVerdict {
    if (templates.isEmpty()) return VpVerdict.UNKNOWN
    val mf = mfccOfTake(pcm) ?: return VpVerdict.UNKNOWN
    var best = Double.MAX_VALUE
    for (t in templates) best = min(best, dtwDistance(mf, t))
    if (best == Double.MAX_VALUE) return VpVerdict.UNKNOWN
    return if (best <= threshold) VpVerdict.MATCH else VpVerdict.MISMATCH
}

/** Combined voice-gate verdict for one heard command. Pure, tested. */
data class VoiceGate(val send: Boolean, val cleaned: String, val bypassGuard: Boolean, val note: String?)

fun voiceGateDecision(
    heard: String, guardOn: Boolean, locked: Boolean,
    masterName: String, enrolled: Boolean, verdict: VpVerdict
): VoiceGate {
    val text = heard.trim()
    if (!guardOn) return VoiceGate(true, text, true, null)
    if (!enrolled || verdict == VpVerdict.UNKNOWN) {
        val g = guardCommand(text, true, locked, masterName)
        return if (g.allowed) VoiceGate(true, g.cleaned, true, null)
        else VoiceGate(false, text, false, "Master voice guard: include your name")
    }
    if (verdict == VpVerdict.MATCH) return VoiceGate(true, text, true, null)
    // MISMATCH: name fallback (required even unlocked); blank name fails closed.
    if (masterName.trim().isEmpty()) return VoiceGate(false, text, false, "Voice didn't match — type to continue")
    val g = guardCommand(text, true, true, masterName)
    return if (g.allowed) VoiceGate(true, g.cleaned, true, "Voice didn't match — name accepted")
    else VoiceGate(false, text, false, "Voice didn't match — say your name")
}

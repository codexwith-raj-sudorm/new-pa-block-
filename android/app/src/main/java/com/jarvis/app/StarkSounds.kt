package com.jarvis.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val RATE = 22050

/**
 * Metallic UI click, 0.09s: noise burst + 2.6kHz ping with fast decay.
 * Pure synthesis (unit-tested), deterministic LCG noise.
 */
fun genClickPcm(): ShortArray {
    val n = (RATE * 0.09).toInt()
    val out = ShortArray(n)
    var seed = 12345L
    for (i in 0 until n) {
        val t = i / RATE.toFloat()
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        val noise = ((seed % 2000) / 1000f - 1f) * 0.5f
        val ping = sin(2 * PI * 2600 * t).toFloat() * 0.5f
        val env = exp(-t * 55f)
        out[i] = ((noise + ping) * env * 28000).toInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}

/**
 * Stark system chime, 0.6s: B5 note answered by an E6 note, both decaying.
 * Pure synthesis (unit-tested).
 */
fun genChimePcm(): ShortArray {
    val n = (RATE * 0.6).toInt()
    val out = ShortArray(n)
    for (i in 0 until n) {
        val t = i / RATE.toFloat()
        val n1 = if (t < 0.3f) sin(2 * PI * 987.77 * t).toFloat() * exp(-t * 6f) else 0f
        val t2 = t - 0.18f
        val n2 = if (t2 > 0) sin(2 * PI * 1318.5 * t2).toFloat() * exp(-t2 * 5f) else 0f
        out[i] = ((n1 * 0.6f + n2 * 0.8f) * 0.7f * 32767).toInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}

/** One-shot Stark interface sounds, synthesized at runtime (no audio assets). */
object StarkSounds {
    private val main = Handler(Looper.getMainLooper())

    fun click() = play(genClickPcm())
    fun chime() = play(genChimePcm())

    private fun play(pcm: ShortArray) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val fmt = AudioFormat.Builder()
                .setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(attrs, fmt, pcm.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            track.write(pcm, 0, pcm.size)
            track.setVolume(0.4f)
            track.play()
            main.postDelayed(
                { runCatching { track.stop(); track.release() } },
                (pcm.size * 1000L / RATE) + 100
            )
        } catch (_: Exception) { }
    }
}

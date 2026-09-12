package com.jarvis.app

import android.media.AudioManager

/**
 * Mutes every non-critical stream so the Google recognizer's start/end beeps
 * can't leak through on any OEM routing (music, system, notification, ...).
 * RING / ALARM / VOICE_CALL are NEVER touched: ringtones, alarms and calls
 * must stay audible. Always pair [mute] with [unmute] (timer + early release).
 */
object SoundMuter {
    private val STREAMS = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_DTMF,
        AudioManager.STREAM_ACCESSIBILITY
    )

    /** Streams we actually muted (subset of [STREAMS]); only these get unmuted. */
    private val mutedByUs = mutableSetOf<Int>()

    @Synchronized
    fun mute(am: AudioManager) {
        // NOTE: never clear here — a second mute() before unmute() (mic retry)
        // must not lose track of streams the first call muted.
        for (s in STREAMS) {
            if (!isMuted(am, s)) {
                runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0) }
                mutedByUs.add(s)
            }
        }
    }

    @Synchronized
    fun unmute(am: AudioManager) {
        for (s in mutedByUs) runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) }
        mutedByUs.clear()
    }

    private fun isMuted(am: AudioManager, stream: Int): Boolean =
        runCatching { am.isStreamMute(stream) }.getOrDefault(false)
}

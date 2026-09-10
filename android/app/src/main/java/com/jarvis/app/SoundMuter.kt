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

    fun mute(am: AudioManager) {
        for (s in STREAMS) runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0) }
    }

    fun unmute(am: AudioManager) {
        for (s in STREAMS) runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) }
    }
}

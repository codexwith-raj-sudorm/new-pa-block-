package com.jarvis.app

import android.speech.SpeechRecognizer

/**
 * Voice-loop helpers: human error text, best-hypothesis picking, stuck-speech
 * detection. 100% JVM-pure (SpeechRecognizer constants are compile-time ints,
 * safe in unit tests) — see VoiceLoopTest.
 */

/** Consecutive transient errors tolerated inside a convo session before giving up. */
const val CONVO_MAX_CONSEC_ERRORS = 3

/** A "speaking" flag older than this with an idle mic is stuck. */
const val SPEAKING_STUCK_MS = 45_000L

/**
 * Friendly text for a recognizer error, or null when it should stay silent
 * (plain user-cancel). No cryptic "(8)" codes in the UI.
 */
fun voiceErrorText(error: Int): String? = when (error) {
    SpeechRecognizer.ERROR_CLIENT -> null
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic is busy — try again in a second"
    SpeechRecognizer.ERROR_NETWORK -> "Voice needs internet — check your connection"
    SpeechRecognizer.ERROR_SERVER -> "Voice service hiccup — try again"
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't open the mic — try again"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed for voice input"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many voice requests — wait a moment"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Voice language isn't supported on this device"
    else -> "Voice hiccup — try again"
}

/**
 * Pick the best hypothesis: highest-confidence non-blank result, falling back
 * to the first non-blank one when scores are missing. Never blank-when-avoidable.
 */
fun bestHeard(results: List<String>?, scores: FloatArray?): String {
    val cands = results.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    if (cands.isEmpty()) return ""
    if (scores == null || scores.size != results!!.size) return cands.first()
    var best = cands.first()
    var bestScore = -1f
    for (i in results.indices) {
        val t = results[i].trim()
        if (t.isEmpty()) continue
        val s = scores[i]
        if (s > bestScore) {
            bestScore = s
            best = t
        }
    }
    return best
}

/** True when the speaking flag is stale (mic idle far too long). Pure, tested. */
fun speakingStuck(speaking: Boolean, nowMs: Long, lastSpeakMs: Long): Boolean =
    speaking && nowMs - lastSpeakMs > SPEAKING_STUCK_MS

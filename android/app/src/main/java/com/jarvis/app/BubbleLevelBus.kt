package com.jarvis.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps SpeechRecognizer RMS dB to a 0..1 UI level.
 * Silence hovers at ~1 or below (stays 0 = bubble still); speech 2..10+ ramps up.
 * Pure function so unit tests can pin the silence/speech boundary.
 */
fun normalizeRms(rms: Float): Float = ((rms - 1f) / 9f).coerceIn(0f, 1f)

/** RMS dB floor for a nearby voice; below this is far-field/background noise. */
const val NEARBY_RMS_DB = 2.5f

/** True when the utterance peaked like a nearby voice. Pure, tested. */
fun isNearbyVoice(peakRmsDb: Float): Boolean = peakRmsDb >= NEARBY_RMS_DB

/** Conversation sessions end after this long with no nearby voice. */
const val CONVO_SILENCE_MS = 10_000L

/** True when the conversation window has run dry. Pure, tested. */
fun convoExpired(nowMs: Long, lastVoiceMs: Long): Boolean = nowMs - lastVoiceMs >= CONVO_SILENCE_MS

/**
 * Live mic-level bus fed by onRmsChanged from whichever recognizer is running
 * (wake loop in [WakeService] or command session in [JarvisVm]). Exactly one
 * recognizer runs at a time by design (mic handoff), so last-writer-wins is safe.
 * The overlay bubble and the in-app mic button observe [level] and pulse with
 * real sound like the Gemini assistant orb; silence = perfectly still.
 */
object BubbleLevelBus {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private var smooth = 0f

    @Synchronized
    fun pushRms(rms: Float) {
        val target = normalizeRms(rms)
        smooth += (target - smooth) * 0.45f
        _level.value = smooth
    }

    @Synchronized
    fun reset() {
        smooth = 0f
        _level.value = 0f
    }
}

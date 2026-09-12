package com.jarvis.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What JARVIS is doing right now (drives bubble color + ticker + waveform). */
data class HudState(
    val listening: Boolean = false,
    val thinking: Boolean = false,
    val speaking: Boolean = false,
    val online: Boolean = true
)

data class Ticker(val text: String, val seq: Long)

/** Process-wide HUD bus: the ViewModel + service publish, the bubble observes. */
object HudStateBus {
    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()
    private val _ticker = MutableStateFlow<Ticker?>(null)
    val ticker: StateFlow<Ticker?> = _ticker.asStateFlow()
    private var seq = 0L

    fun update(listening: Boolean? = null, thinking: Boolean? = null, speaking: Boolean? = null, online: Boolean? = null) {
        val c = _state.value
        _state.value = c.copy(
            listening = listening ?: c.listening,
            thinking = thinking ?: c.thinking,
            speaking = speaking ?: c.speaking,
            online = online ?: c.online
        )
    }

    fun postTicker(text: String) {
        _ticker.value = Ticker(text, ++seq)
    }
}

/**
 * One-shot interrupt requests (e.g. bubble tap while speaking).
 * The UI collects [requests] and routes them to the ViewModel.
 */
object InterruptBus {
    private val _req = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _req.asSharedFlow()
    fun request() {
        _req.tryEmit(Unit)
    }
}

/**
 * Contextual eye color as ARGB (pure, unit-tested).
 * Priority: thinking > listening > speaking > offline > standby.
 */
fun hudAccentArgb(listening: Boolean, thinking: Boolean, speaking: Boolean, online: Boolean): Long = when {
    thinking -> 0xFFF59E0B // amber / gold — cloud thinking
    listening -> 0xFFF8FAFC // white-hot — speech active
    speaking -> 0xFFF8FAFC // white-hot — voice output
    !online -> 0xFF64748B // muted slate — local fallback
    else -> 0xFF22D3EE // electric cyan — standby
}

/**
 * Waveform bar energy 0..1 for bar [index] of [bars] (pure, unit-tested).
 * Silent mic = 0 (rings stay clean); live audio dances with golden-angle spread.
 */
fun waveBarEnergy(index: Int, bars: Int, level: Float, phaseDeg: Float): Float {
    if (level <= 0f || bars <= 0) return 0f
    val wave = 0.5f + 0.5f * kotlin.math.sin(
        index * 2.399f + Math.toRadians(phaseDeg.toDouble()).toFloat() * 3f
    )
    return (level * (0.35f + 0.65f * wave)).coerceIn(0f, 1f)
}

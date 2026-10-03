package com.jarvis.app.backend.gesture

import kotlin.math.max

/** A normalized hand point emitted by the on-device recognizer. */
data class NormalizedPoint(val x: Float, val y: Float)

/** A screen-space point suitable for a visible pointer or accessibility gesture. */
data class ScreenPoint(val x: Float, val y: Float)

/** Minimal immutable observation passed from recognition to the policy layer. */
data class GestureObservation(
    val point: NormalizedPoint?,
    val label: String?,
    val confidence: Float,
    val timestampMs: Long,
)

sealed interface GestureEvent {
    data class PointerMoved(val point: NormalizedPoint) : GestureEvent
    data object Click : GestureEvent
    data object Confirm : GestureEvent
}

/**
 * Maps a normalized camera coordinate into a display rectangle.
 * Front-camera previews are mirrored for the user, so mirrorX should normally
 * be true when the front camera is used.
 */
fun mapToScreen(
    point: NormalizedPoint,
    width: Float,
    height: Float,
    mirrorX: Boolean = true,
    marginFraction: Float = 0f,
): ScreenPoint {
    val margin = marginFraction.coerceIn(0f, 0.45f)
    val x = point.x.coerceIn(0f, 1f)
    val y = point.y.coerceIn(0f, 1f)
    val mappedX = if (mirrorX) 1f - x else x
    val usable = 1f - (margin * 2f)
    return ScreenPoint(
        x = ((margin + mappedX * usable) * width).coerceIn(0f, max(0f, width - 1f)),
        y = ((margin + y * usable) * height).coerceIn(0f, max(0f, height - 1f)),
    )
}

/** Lightweight smoothing that is deterministic and easy to tune on-device. */
class ExponentialPointSmoother(private val alpha: Float = 0.35f) {
    private val blend = alpha.coerceIn(0.01f, 1f)
    private var previous: NormalizedPoint? = null

    fun update(point: NormalizedPoint): NormalizedPoint {
        val next = point.copy(x = point.x.coerceIn(0f, 1f), y = point.y.coerceIn(0f, 1f))
        val old = previous
        val smoothed = if (old == null) {
            next
        } else {
            NormalizedPoint(
                x = old.x + (next.x - old.x) * blend,
                y = old.y + (next.y - old.y) * blend,
            )
        }
        previous = smoothed
        return smoothed
    }

    fun reset() {
        previous = null
    }
}

/**
 * Requires a stable pose before emitting a discrete event and locks until a
 * neutral frame arrives. This prevents a held fist from generating many taps.
 */
class GestureDebouncer(
    private val stableFrames: Int = 4,
    private val cooldownMs: Long = 650L,
) {
    private var candidate: String? = null
    private var candidateFrames = 0
    private var emittedForPose = false
    private var lastEventMs = Long.MIN_VALUE

    fun observe(label: String?, confidence: Float, nowMs: Long): GestureEvent? {
        val usable = label?.takeIf { confidence >= 0.78f && it != "None" }
        if (usable == null) {
            candidate = null
            candidateFrames = 0
            emittedForPose = false
            return null
        }

        if (usable != candidate) {
            candidate = usable
            candidateFrames = 1
            emittedForPose = false
        } else {
            candidateFrames++
        }

        if (candidateFrames < stableFrames || emittedForPose) return null
        if (lastEventMs != Long.MIN_VALUE && nowMs - lastEventMs < cooldownMs) return null

        val event = when (usable) {
            "Closed_Fist" -> GestureEvent.Click
            "Thumb_Up" -> GestureEvent.Confirm
            else -> null
        }
        if (event != null) {
            emittedForPose = true
            lastEventMs = nowMs
        }
        return event
    }
}

/** Combines tracking, smoothing, and discrete event interpretation. */
class GestureInterpreter(alpha: Float = 0.35f) {
    private val smoother = ExponentialPointSmoother(alpha)
    private val debouncer = GestureDebouncer()

    fun observe(
        point: NormalizedPoint?,
        label: String?,
        confidence: Float,
        timestampMs: Long,
    ): List<GestureEvent> {
        if (point == null) {
            smoother.reset()
            debouncer.observe(null, 0f, timestampMs)
            return emptyList()
        }
        val smoothed = smoother.update(point)
        val events = mutableListOf<GestureEvent>(GestureEvent.PointerMoved(smoothed))
        debouncer.observe(label, confidence, timestampMs)?.let(events::add)
        return events
    }
}

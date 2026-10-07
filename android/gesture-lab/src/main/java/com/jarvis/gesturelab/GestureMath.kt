package com.jarvis.gesturelab

import kotlin.math.max

data class NormalizedPoint(val x: Float, val y: Float)

data class GestureObservation(
    val point: NormalizedPoint?,
    val label: String?,
    val confidence: Float,
    val timestampMs: Long,
)

fun mapToPreview(
    point: NormalizedPoint,
    width: Float,
    height: Float,
    mirrorX: Boolean = true,
): NormalizedPoint {
    val x = point.x.coerceIn(0f, 1f)
    val y = point.y.coerceIn(0f, 1f)
    return NormalizedPoint(if (mirrorX) 1f - x else x, y)
}

class ExponentialPointSmoother(private val alpha: Float = 0.5f) {
    private val blend = alpha.coerceIn(0.01f, 1f)
    private var previous: NormalizedPoint? = null

    fun update(point: NormalizedPoint): NormalizedPoint {
        val next = point.copy(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        val old = previous
        val smoothed = if (old == null) next else NormalizedPoint(
            old.x + (next.x - old.x) * blend,
            old.y + (next.y - old.y) * blend,
        )
        previous = smoothed
        return smoothed
    }

    fun reset() { previous = null }
}

fun formatDataSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024f
    if (kb < 1024f) return "${"%.0f".format(kb)} KB"
    return "${"%.1f".format(kb / 1024f)} MB"
}

package com.jarvis.app.backend.system

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** §8 Dynamic Island: drops from the camera cutout when standby arms. */

const val ISLAND_HIDDEN = 0
const val ISLAND_PILL = 1
const val ISLAND_DOT = 2
const val ISLAND_OVERRIDE = 3

const val ISLAND_PILL_MS = 5_000L
const val ISLAND_OVERRIDE_MS = 3_000L

val IslandGreen = Color(0xFF17C964)

/**
 * Pure: island mode from armed state + elapsed times. Pill on arm, dot after
 * 5s, override wins for 3s after a wake strike, hidden when disarmed. Tested.
 */
fun islandMode(armed: Boolean, sinceArmedMs: Long, sinceOverrideMs: Long?): Int {
    if (!armed) return ISLAND_HIDDEN
    if (sinceOverrideMs != null && sinceOverrideMs < ISLAND_OVERRIDE_MS) return ISLAND_OVERRIDE
    if (sinceArmedMs < ISLAND_PILL_MS) return ISLAND_PILL
    return ISLAND_DOT
}

/**
 * Horizontal bias (0..1) of the camera cutout center for island alignment.
 * Falls back to screen center when the cutout is unknown (API < 30, tablets).
 */
fun cutoutBias(wm: WindowManager): Float {
    return try {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.maximumWindowMetrics
            val rect = metrics.windowInsets.displayCutout?.boundingRects?.firstOrNull()
            val w = metrics.bounds.width()
            if (rect != null && w > 0) {
                (rect.centerX().toFloat() / w).coerceIn(0.15f, 0.85f)
            } else 0.5f
        } else 0.5f
    } catch (_: Exception) {
        0.5f
    }
}

@Composable
private fun IslandWaveform(level: Float, phaseDeg: Float, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val e = waveBarEnergy(i, 5, level.coerceAtLeast(0.12f), phaseDeg)
            Box(
                Modifier.width(3.dp).height((4 + e * 14).dp)
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * The island HUD: black pill with neon waveform + mono readout, collapsing
 * to a glowing dot tethered to the cutout. Pure indicator (non-touchable).
 */
@Composable
fun StandbyIslandHud(bias: Float) {
    val armed by StandbyBus.armed.collectAsState()
    val level by BubbleLevelBus.level.collectAsState()
    var armedAt by remember { mutableStateOf(0L) }
    var overrideAt by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var phase by remember { mutableStateOf(0f) }

    LaunchedEffect(armed) {
        if (armed) {
            armedAt = System.currentTimeMillis()
            overrideAt = null
        }
    }
    LaunchedEffect(Unit) {
        StandbyBus.overrides.collect { overrideAt = it }
    }
    // 2fps clock drives the pure mode machine + waveform phase.
    LaunchedEffect(armed) {
        while (armed) {
            now = System.currentTimeMillis()
            phase = (phase + 45f) % 360f
            delay(500)
        }
    }

    val mode = islandMode(armed, now - armedAt, overrideAt?.let { now - it })
    if (mode == ISLAND_HIDDEN) return

    val expanded = mode == ISLAND_PILL || mode == ISLAND_OVERRIDE
    val w by animateDpAsState(
        if (expanded) 228.dp else 12.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "isl_w"
    )
    val h by animateDpAsState(
        if (expanded) 36.dp else 12.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "isl_h"
    )
    val isOverride = mode == ISLAND_OVERRIDE

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cx = (maxWidth * bias).coerceIn(w / 2 + 8.dp, maxWidth - w / 2 - 8.dp)
        Box(
            Modifier.offset(x = cx - w / 2, y = 12.dp)
                .width(w).height(h)
                .shadow(if (expanded) 16.dp else 6.dp, CircleShape)
                .background(Color.Black, CircleShape)
                .border(
                    1.dp,
                    if (isOverride || !expanded) IslandGreen.copy(alpha = 0.6f)
                    else Color.White.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IslandWaveform(level, phase, IslandGreen)
                    Text(
                        if (isOverride) "[SYS] OVERRIDE" else "[SYS] LISTENING",
                        color = if (isOverride) IslandGreen else Color(0xFFD1D5DB),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Box(Modifier.width(6.dp).height(6.dp).background(IslandGreen, CircleShape))
            }
        }
    }
}

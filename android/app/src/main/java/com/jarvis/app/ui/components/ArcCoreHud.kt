package com.jarvis.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Arc-core HUD palette. */
val HudCyan = Color(0xFF67E8F9)
val HudBlue = Color(0xFF38BDF8)
val HudAmber = Color(0xFFFBBF24)
val HudGold = Color(0xFFFFBA27)
val HudInk = Color(0xFFE6EDF3)

/** One-line status for the HUD bar. Pure, tested. */
fun hudStatusLine(online: Boolean, wakeOn: Boolean): String {
    val link = if (online) "NEURAL LINK ACTIVE" else "OFFLINE"
    return if (wakeOn) "$link \u2022 WAKE ARMED" else link
}

/** Core state label. Priority: speaking > listening > thinking > standby. Pure, tested. */
fun coreStateLabel(listening: Boolean, thinking: Boolean, speaking: Boolean): String = when {
    speaking -> "SPEAKING"
    listening -> "LISTENING"
    thinking -> "THINKING"
    else -> "STANDBY"
}

/** Full revolution time (ms) for the reactor rings. Pure, tested. */
fun arcSpinMs(thinking: Boolean, listening: Boolean): Int = when {
    thinking -> 1200
    listening -> 2600
    else -> 9000
}

/** Readout line for the HUD hero. Pure, tested. */
fun hudReadoutLine(temp: String, ping: String, batt: Int): String {
    val t = temp.ifBlank() { "\u2014" }
    val p = ping.ifBlank() { "\u2014" }
    val b = if (batt < 0) "\u2014" else "$batt%"
    return "SYS $t \u2022 NET $p \u2022 PWR $b"
}

/** Deterministic per-bar height from mic level (0.08..1). Pure, tested. */
fun acousticBarHeight(level: Float, index: Int): Float {
    val l = level.coerceIn(0f, 1f)
    val frac = (sin(index * 12.9898f) * 43758.5453f).let { it - kotlin.math.floor(it) }
    return (0.08f + 0.92f * l * (0.35f + 0.65f * frac)).coerceIn(0.08f, 1f)
}

/** Every 5th bar is cyan (reference contrast rhythm). Pure, tested. */
fun acousticBarCyan(index: Int): Boolean = index % 5 == 0

/**
 * Reactive arc-core: tick ring, counter-rotating coil arcs, glowing core that
 * breathes with the mic/speech level, radar sweep while listening. Tap = interrupt.
 */
@Composable
fun ArcCoreReactor(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    level: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp
) {
    val spinMs = arcSpinMs(thinking, listening)
    val stateColor = when {
        speaking -> HudGold
        listening -> HudCyan
        thinking -> HudBlue
        else -> HudCyan.copy(alpha = 0.7f)
    }
    val spin by key(spinMs) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(spinMs, easing = LinearEasing)),
            label = "spin"
        )
    }
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val active = if (speaking || listening) 1f else 0.35f
    Canvas(modifier.size(sizeDp).clickable(onClick = onTap)) {
        val r = size.minDimension / 2f
        val c = center
        val lvl = level.coerceIn(0f, 1f)
        // Outer tick ring.
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val long = i % 5 == 0
            val r1 = r * (if (long) 0.90f else 0.94f)
            val r2 = r * 0.98f
            drawLine(
                stateColor.copy(alpha = if (long) 0.8f else 0.3f),
                Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                strokeWidth = if (long) 3f else 2f
            )
        }
        // Counter-rotating dashed coil arcs.
        val rr = r * 0.78f
        val arcTopLeft = Offset(c.x - rr, c.y - rr)
        val arcSize = Size(rr * 2, rr * 2)
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
        drawArc(
            stateColor.copy(alpha = 0.55f), spin, 270f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 5f, pathEffect = dash)
        )
        drawArc(
            stateColor.copy(alpha = 0.35f), -spin, 200f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 3f, pathEffect = dash)
        )
        // Coil nodes riding the slow ring.
        for (i in 0 until 10) {
            val a = Math.toRadians((i * 36f + spin / 3f).toDouble())
            val cr = r * 0.64f
            drawCircle(
                HudGold.copy(alpha = 0.9f),
                radius = r * 0.042f,
                center = Offset(c.x + cr * cos(a).toFloat(), c.y + cr * sin(a).toFloat())
            )
        }
        // Orbiting synaptic spark.
        val sa = Math.toRadians((spin * 2.5).toDouble())
        val sc = Offset(c.x + rr * cos(sa).toFloat(), c.y + rr * sin(sa).toFloat())
        drawCircle(HudGold.copy(alpha = 0.25f), radius = r * 0.06f, center = sc)
        drawCircle(Color.White, radius = r * 0.022f, center = sc)
        // Breathing core.
        val coreR = r * 0.42f * (1f + 0.05f * lvl + 0.04f * pulse * active)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, HudGold, Color.Transparent),
                center = c, radius = coreR * 1.7f
            ),
            radius = coreR * 1.7f, center = c
        )
        drawCircle(Color.White, radius = coreR * 0.32f, center = c)
        // Radar sweep while listening.
        if (listening) {
            drawArc(
                Color.White.copy(alpha = 0.85f), spin, 40f, false,
                topLeft = arcTopLeft, size = arcSize,
                style = Stroke(width = 6f)
            )
        }
    }
}

/** Dark HUD backdrop: gradient + faint grid + corner brackets. */
@Composable
fun HudBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05090F))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1626), Color(0xFF05090F), Color(0xFF0A0F1A))
                )
            )
            val step = 48.dp.toPx()
            val grid = HudBlue.copy(alpha = 0.05f)
            var x = 0f
            while (x < size.width) {
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            val len = 30f
            val m = 12f
            val bc = HudCyan.copy(alpha = 0.4f)
            val w = 3f
            val sw = size.width
            val sh = size.height
            drawLine(bc, Offset(m, m + len), Offset(m, m), w)
            drawLine(bc, Offset(m, m), Offset(m + len, m), w)
            drawLine(bc, Offset(sw - m, m + len), Offset(sw - m, m), w)
            drawLine(bc, Offset(sw - m, m), Offset(sw - m - len, m), w)
            drawLine(bc, Offset(m, sh - m - len), Offset(m, sh - m), w)
            drawLine(bc, Offset(m, sh - m), Offset(m + len, sh - m), w)
            drawLine(bc, Offset(sw - m, sh - m - len), Offset(sw - m, sh - m), w)
            drawLine(bc, Offset(sw - m, sh - m), Offset(sw - m - len, sh - m), w)
        }
        content()
    }
}

/** Gold waveform strip (every 5th bar cyan), driven by the live mic level. */
@Composable
fun AcousticArray(level: Float, modifier: Modifier = Modifier) {
    val barCount = 24
    Canvas(modifier.height(52.dp).fillMaxWidth()) {
        val gap = 4f
        val bw = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val h = size.height * acousticBarHeight(level, i)
            drawRect(
                if (acousticBarCyan(i)) HudCyan else HudGold,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h)
            )
        }
    }
}

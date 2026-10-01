package com.jarvis.app.frontend.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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

/** Core state label. Priority: speaking > listening > thinking > convo > standby. Pure, tested. */
fun coreStateLabel(listening: Boolean, thinking: Boolean, speaking: Boolean, convo: Boolean = false): String = when {
    speaking -> "SPEAKING"
    listening -> "LISTENING"
    thinking -> "THINKING"
    convo -> "CONVO LIVE"
    else -> "STANDBY"
}

/** Compact ping tag for the hub readout ("42 ms" -> "42MS"). Pure, tested. */
fun fmtPingTag(ping: String): String =
    ping.uppercase().replace(" ", "").ifBlank { "\u2014" }

/** Voice language tag for the hub readout. Pure, tested. */
fun voiceTag(hindi: Boolean): String = if (hindi) "VOICE \u2022 HI" else "VOICE \u2022 EN"

/** Full revolution time (ms) for the reactor rings. Pure, tested. */
fun arcSpinMs(thinking: Boolean, listening: Boolean): Int = when {
    thinking -> 1200
    listening -> 2600
    else -> 9000
}

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

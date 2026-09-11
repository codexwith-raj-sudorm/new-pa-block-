package com.jarvis.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stark-style HUD bubble: pulsing arc-reactor core + rotating telemetry ring.
 *
 * - [level] (0..1 live mic energy) + [hudActive] morph the clean rings into a
 *   kinetic audio waveform while JARVIS listens or speaks.
 * - Drag release spring-snaps the bubble to the nearest screen edge, tucked.
 * - [accent] recolors the glow/rings/core for state (cyan/green/amber/gray).
 * - [contentWidthDp] is the host window width, used to center on screen edges.
 */
@Composable
fun StarkBubble(
    modifier: Modifier = Modifier,
    startX: Float = 24f,
    startY: Float = 320f,
    contentWidthDp: Float = 120f,
    level: Float = 0f,
    hudActive: Boolean = false,
    accent: Color = Color(0xFF22D3EE),
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    var offsetX by remember { mutableStateOf(startX) }
    var offsetY by remember { mutableStateOf(startY) }
    val active = hudActive || level > 0.04f

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    // Infinite transitions for cinematic HUD animations
    val infiniteTransition = rememberInfiniteTransition(label = "stark_hud")

    // Pulse animation for the inner core
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    // Rotation angle for the outer telemetry ticks (also drives the waveform dance)
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Morph between clean rings and the kinetic waveform
    val activeMix by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(250),
        label = "wave_mix"
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(16.dp, CircleShape, spotColor = accent)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        onPositionChanged(offsetX, offsetY)
                    },
                    onDragEnd = {
                        // Quantum snap-to-edge: glide to the nearest edge, tucked.
                        val screenWpx = with(density) { configuration.screenWidthDp.dp.toPx() }
                        val centerPx = with(density) { (contentWidthDp / 2).dp.toPx() }
                        val marginPx = with(density) { 8.dp.toPx() }
                        val bubbleCenterX = offsetX + centerPx
                        val targetCenterX =
                            if (bubbleCenterX < screenWpx / 2) marginPx else screenWpx - marginPx
                        val targetX = targetCenterX - centerPx
                        scope.launch {
                            Animatable(offsetX).animateTo(
                                targetX,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) {
                                offsetX = value
                                onPositionChanged(offsetX, offsetY)
                            }
                        }
                    }
                )
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0E7490).copy(alpha = 0.8f),
                        Color(0xFF0B1220).copy(alpha = 0.95f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Canvas drawing the Sci-Fi HUD tactical rings and ticks
        Canvas(modifier = Modifier.size(72.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 4.dp.toPx()

            if (activeMix < 0.99f) {
                // Outer segmented tracking ring
                drawCircle(
                    color = accent.copy(alpha = 0.4f * (1f - activeMix)),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner technical ring
                drawCircle(
                    color = accent.copy(alpha = 0.7f * (1f - activeMix)),
                    radius = radius * 0.75f,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10f, 10f), 0f
                        )
                    )
                )
            }

            if (activeMix > 0.01f) {
                // Kinetic audio waveform: radiating bars dancing with live level.
                val bars = 24
                val baseR = radius + 2.dp.toPx()
                for (i in 0 until bars) {
                    val aRad = Math.toRadians((i * 360.0 / bars))
                    val dir = Offset(cos(aRad).toFloat(), sin(aRad).toFloat())
                    val e = waveBarEnergy(i, bars, level, rotationAngle)
                    val len = (2 + 15 * e).dp.toPx()
                    drawLine(
                        color = accent.copy(alpha = activeMix),
                        start = center + dir * baseR,
                        end = center + dir * (baseR + len),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Dynamic rotating crosshairs / notches
            val notchAngleRad = Math.toRadians(rotationAngle.toDouble())
            val x1 = center.x + (radius * 0.75f) * cos(notchAngleRad).toFloat()
            val y1 = center.y + (radius * 0.75f) * sin(notchAngleRad).toFloat()
            drawCircle(accent, radius = 2.dp.toPx(), center = Offset(x1, y1))
        }

        // Pulsing Miniature Arc Reactor Core (swells with voice while active)
        Box(
            modifier = Modifier
                .size((28 * coreScale * (1f + 0.3f * activeMix * level)).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            accent.copy(alpha = 0.65f),
                            accent
                        )
                    )
                )
        )
    }
}

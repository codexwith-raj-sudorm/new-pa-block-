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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stark-style HUD bubble: pulsing arc-reactor core + rotating telemetry ring.
 *
 * Adaptations from the supplied design (look unchanged by default):
 * - [onClick] is wired to taps (was never fired).
 * - [startX]/[startY] seed the drag offsets so the window never jumps.
 * - [accent] recolors the glow/rings/core for state (cyan armed, red command, green flash).
 */
@Composable
fun StarkBubble(
    modifier: Modifier = Modifier,
    startX: Float = 24f,
    startY: Float = 320f,
    accent: Color = Color(0xFF22D3EE),
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    var offsetX by remember { mutableStateOf(startX) }
    var offsetY by remember { mutableStateOf(startY) }

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

    // Rotation angle for the outer telemetry ticks
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
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

            // Outer segmented tracking ring
            drawCircle(
                color = accent.copy(alpha = 0.4f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Inner technical ring
            drawCircle(
                color = accent.copy(alpha = 0.7f),
                radius = radius * 0.75f,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashArrayEffect(floatArrayOf(10f, 10f), 0f)
                )
            )

            // Dynamic rotating crosshairs / notches
            val notchAngleRad = Math.toRadians(rotationAngle.toDouble())
            val x1 = center.x + (radius * 0.75f) * cos(notchAngleRad).toFloat()
            val y1 = center.y + (radius * 0.75f) * sin(notchAngleRad).toFloat()
            drawCircle(accent, radius = 2.dp.toPx(), center = Offset(x1, y1))
        }

        // Pulsing Miniature Arc Reactor Core
        Box(
            modifier = Modifier
                .size((28 * coreScale).dp)
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

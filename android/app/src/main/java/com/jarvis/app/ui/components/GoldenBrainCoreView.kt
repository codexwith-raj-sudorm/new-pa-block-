package com.jarvis.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GoldenBrainCoreView(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 96.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mind_matrix_transition")

    // Breathing pulse for the central core (0.85 to 1.15 over 1s) - FR-1
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    // Primary 4s rotation for orbital spark and longitude rings - FR-1
    val primaryRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primary_rotation"
    )

    // Counter-rotation (6s) to create the multi-layered 3D holographic effect
    val secondaryRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondary_rotation"
    )

    // Interactive circular tap target with ripple - FR-2
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = Color(0xFFFDE68A)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f - 4.dp.toPx()
            if (baseRadius <= 0f) return@Canvas

            // 1. OUTER TELEMETRY / FIELD HALO (FR-1)
            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.35f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 2. TILTED 3D NEURAL LATTICE RINGS (The Mind Stone / Ultron Matrix)
            // Equator latitude ring oscillating to simulate 3D pitch
            val pitchFactor = abs(cos(Math.toRadians(primaryRotation.toDouble()))).toFloat().coerceAtLeast(0.15f)
            drawOval(
                color = Color(0xFFFBBF24).copy(alpha = 0.6f),
                topLeft = Offset(center.x - baseRadius * 0.9f, center.y - (baseRadius * 0.9f * pitchFactor)),
                size = Size(baseRadius * 1.8f, baseRadius * 1.8f * pitchFactor),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Inclined Shell A (45-degree angle dashed matrix ring - FR-1)
            rotate(degrees = 45f + (primaryRotation * 0.2f), pivot = center) {
                drawOval(
                    color = Color(0xFFF59E0B).copy(alpha = 0.75f),
                    topLeft = Offset(center.x - baseRadius * 0.75f, center.y - (baseRadius * 0.35f)),
                    size = Size(baseRadius * 1.5f, baseRadius * 0.7f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }

            // Inclined Shell B (-45-degree counter-rotating sphere slice)
            rotate(degrees = -45f - (secondaryRotation * 0.25f), pivot = center) {
                drawOval(
                    color = Color(0xFFFDE68A).copy(alpha = 0.5f),
                    topLeft = Offset(center.x - baseRadius * 0.8f, center.y - (baseRadius * 0.4f)),
                    size = Size(baseRadius * 1.6f, baseRadius * 0.8f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )
            }

            // 3. SYNAPTIC NEURAL RADIATIONS (Spokes connecting center to surface)
            val radAngle = Math.toRadians(secondaryRotation.toDouble())
            for (i in 0 until 6) {
                val spokeAngle = radAngle + (i * (Math.PI / 3))
                val startX = center.x + (baseRadius * 0.2f * cos(spokeAngle)).toFloat()
                val startY = center.y + (baseRadius * 0.2f * sin(spokeAngle)).toFloat()
                val endX = center.x + (baseRadius * 0.72f * cos(spokeAngle)).toFloat()
                val endY = center.y + (baseRadius * 0.72f * sin(spokeAngle)).toFloat()

                drawLine(
                    color = Color(0xFFFBBF24).copy(alpha = 0.3f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 4. ORBITING SPARK / SYNAPSE NODE (FR-1)
            val sparkAngleRad = Math.toRadians(primaryRotation.toDouble())
            val sparkX = center.x + (baseRadius * 0.75f * cos(sparkAngleRad)).toFloat()
            val sparkY = center.y + (baseRadius * 0.75f * sin(sparkAngleRad)).toFloat()

            // Spark halo glow
            drawCircle(
                color = Color(0xFFFEF3C7),
                radius = 3.dp.toPx(),
                center = Offset(sparkX, sparkY)
            )
            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.5f),
                radius = 6.dp.toPx(),
                center = Offset(sparkX, sparkY)
            )

            // 5. RADIATING GOLDEN BRAIN CORE NUCLEUS (FR-1)
            val coreRadius = (baseRadius * 0.38f) * corePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFFEF3C7),
                        Color(0xFFFBBF24),
                        Color(0xFFD97706),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }
    }
}

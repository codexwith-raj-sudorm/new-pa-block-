package com.jarvis.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GoldenBrainCoreView(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "golden_core_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "core_spin"
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 6.dp.toPx()

            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.5f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = Color(0xFFFBBF24).copy(alpha = 0.8f),
                radius = radius * 0.7f,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashArrayEffect(floatArrayOf(10f, 6f), 0f)
                )
            )

            val rad = Math.toRadians(spinAngle.toDouble())
            val x = center.x + (radius * 0.7f) * cos(rad).toFloat()
            val y = center.y + (radius * 0.7f) * sin(rad).toFloat()
            drawCircle(Color(0xFFFEF3C7), radius = 3.dp.toPx(), center = Offset(x, y))
        }

        Box(
            modifier = Modifier
                .size((36 * pulseScale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFFEF3C7),
                            Color(0xFFFBBF24),
                            Color(0xFFF59E0B)
                        )
                    )
                )
        )
    }
}

package com.jarvis.app.frontend.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Premium green tokens (from design_md "Premium UI" mockup). */
val PremiumNeon = Color(0xFF17C964)
val PremiumJade = Color(0xFF10B981)
val PremiumMint = Color(0xFF34D399)
val PremiumGlass = Color(0x731E1E23)
val PremiumGlassEdge = Color(0x0FFFFFFF)
val PremiumMuted = Color(0xFF9CA3AF)

/** Frosted dark-glass surface. */
fun Modifier.premiumGlass(shape: Shape): Modifier =
    this.background(PremiumGlass, shape).border(1.dp, PremiumGlassEdge, shape)

/** First letter for the avatar. Pure, tested. */
fun avatarLetter(name: String): String =
    name.trim().firstOrNull()?.uppercase() ?: "J"

/** Home hero shows only on a fresh chat. Pure, tested. */
fun premiumHeroVisible(msgCount: Int, busy: Boolean): Boolean = msgCount <= 1 && !busy

/** Short model tag for the context card. Pure, tested. */
fun modelShortName(model: String): String =
    model.substringAfterLast("/").ifBlank { "default" }

/** Organic blob radius for the swirl rings. Pure, tested. */
fun blobPointRadius(baseR: Float, angleRad: Float, lobes: Int, wobble: Float, phase: Float): Float =
    baseR * (1f + wobble * kotlin.math.cos(lobes * angleRad + phase))

/** Morphing organic core: 3 counter-rotating blob rings over the ambient glow. */
@Composable
fun SwirlCore(level: Float, modifier: Modifier = Modifier, diameter: Dp = 280.dp) {
    val t = rememberInfiniteTransition(label = "swirl")
    val a1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "r1")
    val a2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "r2")
    val a3 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "r3")
    val pulse by t.animateFloat(1f, 1.05f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "p")
    val s = pulse + level * 0.12f
    Canvas(modifier.size(diameter)) {
        drawBlobRing(PremiumNeon, 1f, a1, s, 3, 0.10f, 1f)
        drawBlobRing(PremiumJade, 0.9f, a2, s, 4, 0.08f, 1f)
        drawBlobRing(PremiumMint, 0.8f, a3, s, 5, 0.06f, 0.7f)
    }
}

private fun DrawScope.drawBlobRing(
    color: Color, frac: Float, angleDeg: Float, scale: Float,
    lobes: Int, wobble: Float, alpha: Float
) {
    val baseR = size.minDimension / 2f * frac * scale
    val path = Path()
    val n = 120
    for (i in 0..n) {
        val th = i.toFloat() / n * (2f * Math.PI.toFloat())
        val r = blobPointRadius(baseR, th, lobes, wobble, 0f)
        val x = center.x + r * kotlin.math.cos(th)
        val y = center.y + r * kotlin.math.sin(th)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    rotate(angleDeg, center) {
        drawPath(path, color.copy(alpha = 0.22f * alpha), style = Stroke(width = 9.dp.toPx()))
        drawPath(path, color.copy(alpha = 0.55f * alpha), style = Stroke(width = 3.dp.toPx()))
    }
}

/** Pure-black backdrop with the ambient glow + swirl core behind the content. */
@Composable
fun PremiumBackdrop(level: Float, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.background(Color.Black)) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(PremiumNeon.copy(alpha = 0.22f + level.coerceIn(0f, 1f) * 0.25f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.4f),
                    radius = size.minDimension * 0.55f
                )
            )
        }
        content()
    }
}

/** Round frosted-glass icon button for the header. */
@Composable
fun GlassCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).premiumGlass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Solid green pill action. */
@Composable
fun NeonPillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = PremiumNeon, contentColor = Color.Black),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
    ) {
        Text(label, fontWeight = FontWeight.Medium)
    }
}

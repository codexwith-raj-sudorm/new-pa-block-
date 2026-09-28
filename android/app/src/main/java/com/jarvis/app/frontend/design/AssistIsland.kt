package com.jarvis.app.frontend.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animated "LISTENING" dots step. Pure, tested. */
fun listeningDots(step: Int): String = when ((step % 4 + 4) % 4) {
    1 -> "."
    2 -> ".."
    3 -> "..."
    else -> ""
}

/** Swipe-down travel (px) that dismisses the island. Pure, tested. */
fun islandDragCloses(totalDyPx: Float): Boolean = totalDyPx > 40f

/**
 * Edge-flash envelope 0..1 over the 2.5s fire: peaks at 15%, eases to 0.35
 * by half-time, gone at the end (mirrors the prototype keyframes). Pure, tested.
 */
fun edgeFlashAlpha(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.15f -> p / 0.15f
        p < 0.5f -> 1f - (p - 0.15f) / 0.35f * 0.65f
        else -> 0.35f * (1f - (p - 0.5f) / 0.5f)
    }
}

/**
 * Flow-border envelope 0..1 over the 1.5s trace: fades in over the first
 * 10%, holds, fades out over the last 20%. Pure, tested.
 */
fun flowBorderAlpha(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.1f -> p / 0.1f
        p < 0.8f -> 1f
        else -> 1f - (p - 0.8f) / 0.2f
    }
}

/**
 * Full-screen neon edge flash fired when the island opens (2.5s, one-shot).
 * Re-fires whenever [fireTick] changes.
 */
@Composable
fun EdgeFlashOverlay(fireTick: Int) {
    var progress by remember(fireTick) { mutableStateOf(0f) }
    LaunchedEffect(fireTick) {
        animate(
            0f, 1f,
            animationSpec = tween(2500, easing = CubicBezierEasing(0.1f, 0.8f, 0.3f, 1f))
        ) { v, _ -> progress = v }
    }
    if (progress >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val a = edgeFlashAlpha(progress)
        if (a <= 0f) return@Canvas
        // Layered inset strokes fake the CSS inset box-shadow bloom.
        drawRoundRect(
            PremiumNeon.copy(alpha = 0.14f * a), cornerRadius = CornerRadius(28.dp.toPx()),
            style = Stroke(width = 52.dp.toPx())
        )
        drawRoundRect(
            PremiumNeon.copy(alpha = 0.25f * a), cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 28.dp.toPx())
        )
        drawRoundRect(
            PremiumNeon.copy(alpha = 0.45f * a), cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(width = 12.dp.toPx())
        )
        drawRoundRect(
            PremiumNeon.copy(alpha = 0.85f * a), cornerRadius = CornerRadius(18.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

/** 56dp AI core: pulsing glow dot + dashed/spinning + dotted/counter-spinning tracks. */
@Composable
fun AssistCoreDot(size: Dp = 56.dp, onTap: () -> Unit) {
    val t = rememberInfiniteTransition(label = "assist_core")
    val outer by t.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "o")
    val inner by t.animateFloat(360f, 0f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "i")
    val pulse by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pu")
    Canvas(Modifier.size(size).clickable(onClick = onTap)) {
        val dotR = 10.dp.toPx() * pulse
        drawCircle(PremiumNeon.copy(alpha = 0.30f), radius = dotR * 2f)
        drawCircle(PremiumNeon, radius = dotR)
        drawCircle(Color.White.copy(alpha = 0.85f), radius = dotR * 0.45f)
        rotate(outer, center) {
            drawArc(
                PremiumNeon.copy(alpha = 0.4f), 0f, 360f, false,
                style = Stroke(
                    2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))
                )
            )
        }
        rotate(inner, center) {
            val pad = size.minDimension * 0.15f
            drawArc(
                PremiumNeon.copy(alpha = 0.9f), 0f, 360f, false,
                topLeft = Offset(pad, pad),
                size = androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height - pad * 2f),
                style = Stroke(
                    2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7.dp.toPx()))
                )
            )
        }
    }
}

/** Ghost pill companion to [NeonPillButton]. */
@Composable
fun GhostPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x0DFFFFFF), contentColor = Color.White
        ),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Floating island sheet: slides up on open, sweeps the neon border trace
 * once, swipe-down (>40px) or backdrop tap dismisses.
 */
@Composable
fun AssistIslandSheet(
    stateLabel: String,
    heard: String,
    listening: Boolean,
    thinking: Boolean,
    onCoreTap: () -> Unit,
    onAskScreen: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var borderP by remember { mutableStateOf(0f) }
    var dotStep by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        shown = true
        delay(100)
        animate(0f, 1f, animationSpec = tween(1500, easing = LinearEasing)) { v, _ -> borderP = v }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotStep++
        }
    }
    val offsetY by animateDpAsState(
        if (shown) 0.dp else 420.dp,
        animationSpec = tween(500, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)),
        label = "island_slide"
    )
    val sheetShape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset(y = offsetY)
            .pointerInput(onClose) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (islandDragCloses(total)) onClose()
                        total = 0f
                    },
                    onVerticalDrag = { _, dy -> if (dy > 0f) total += dy }
                )
            }
    ) {
        // Neon border trace sweeping top -> bottom behind the card.
        Canvas(Modifier.matchParentSize()) {
            val a = flowBorderAlpha(borderP)
            if (a > 0f) {
                val bandH = size.height * 0.5f
                val top = borderP * (size.height + bandH) - bandH
                drawRoundRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to PremiumNeon.copy(alpha = a),
                        1f to Color.Transparent,
                        start = Offset(0f, top),
                        end = Offset(0f, top + bandH)
                    ),
                    cornerRadius = CornerRadius(32.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .background(Color(0xD9121216), sheetShape)
                .border(1.dp, Color(0x14FFFFFF), sheetShape)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(PremiumNeon, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "JARVIS LINK", color = Color(0xFF6B7280), fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistCoreDot(56.dp, onCoreTap)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "JARVIS // ASSIST", color = Color(0xFF6B7280), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stateLabel.uppercase(), color = PremiumNeon, fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                        )
                        if (listening || thinking) {
                            Text(
                                listeningDots(dotStep), color = PremiumNeon, fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            if (heard.isNotBlank() && heard != "…") {
                Spacer(Modifier.height(8.dp))
                Text(
                    heard, color = Color(0xFFE2E8F0), fontSize = 13.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonPillButton("ASK ABOUT SCREEN", onAskScreen, Modifier.weight(1f))
                GhostPillButton("OPEN", onOpen, Modifier.width(80.dp))
            }
        }
    }
}

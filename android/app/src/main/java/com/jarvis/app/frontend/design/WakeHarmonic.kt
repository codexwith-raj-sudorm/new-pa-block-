package com.jarvis.app.frontend.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.backend.brain.JarvisViewModel
import com.jarvis.app.backend.system.BubbleLevelBus
import com.jarvis.app.backend.system.HudStateBus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Staggered ripple phase 0..1 for ring [index] (0..2). Pure, tested. */
fun ripplePhase(progress: Float, index: Int): Float {
    val p = (progress + index / 3f) % 1f
    return if (p < 0f) p + 1f else p
}

/** Ripple ring scale 0.8 -> 2.8 over its life. Pure, tested. */
fun rippleScale(phase: Float): Float = 0.8f + 2.0f * phase

/** Ripple ring alpha 0.8 -> 0 over its life. Pure, tested. */
fun rippleAlpha(phase: Float): Float = 0.8f * (1f - phase)

enum class HubState { IDLE, WAKE, LISTENING }

/** Hub bubble state: live audio wins, then the wake flash. Pure, tested. */
/** Edge to dock (-1 left, +1 right) from window x. Pure, tested. */
fun nearestDockSide(xPx: Float, centerPx: Float, screenWPx: Float): Int =
    if (xPx + centerPx < screenWPx / 2) -1 else 1

fun hubStateFor(listening: Boolean, speaking: Boolean, wakeFlash: Boolean): HubState = when {
    listening || speaking -> HubState.LISTENING
    wakeFlash -> HubState.WAKE
    else -> HubState.IDLE
}

/** Hub label: honest about mic vs speaker. Pure, tested. */
fun hubLabelFor(listening: Boolean, speaking: Boolean, wakeFlash: Boolean): String = when {
    listening -> "[MIC: LIVE]"
    speaking -> "[VOICE: LIVE]"
    wakeFlash -> "[WAKE: RECOGNIZED]"
    else -> "[SYS: IDLE]"
}

/** Track spin period per state (ms). Pure, tested. */
fun hubSpinMs(state: HubState): Int = when (state) {
    HubState.IDLE -> 15000
    HubState.WAKE -> 4000
    HubState.LISTENING -> 8000
}

/** Track spin direction (listening runs reversed). Pure, tested. */
fun hubSpinDir(state: HubState): Float = if (state == HubState.LISTENING) -1f else 1f

/** Core pulse period per state (ms). Pure, tested. */
fun hubPeriodMs(state: HubState): Int = when (state) {
    HubState.IDLE -> 3000
    HubState.WAKE -> 300
    HubState.LISTENING -> 1000
}

/** Core (scale, alpha) at [phase] 0..1. Pure, tested. */
fun hubPulse(state: HubState, phase: Float): Pair<Float, Float> {
    val t = 1f - abs(2f * phase - 1f)
    return when (state) {
        HubState.IDLE -> Pair(0.9f + 0.2f * t, 0.7f + 0.3f * t)
        HubState.WAKE -> Pair(1f + 0.3f * t, 0.9f + 0.1f * t)
        HubState.LISTENING -> {
            val s = if (phase < 0.7f) 0.8f + 0.3f * (phase / 0.7f)
            else 1.1f - 0.3f * ((phase - 0.7f) / 0.3f)
            Pair(s, 1f)
        }
    }
}

/**
 * Full-screen wake-mode interface: harmonic core over the shared ViewModel.
 * Same contract as the hologram HUD it replaces (mic / expand / close).
 */
@Composable
fun WakeHarmonicHud(
    vm: JarvisViewModel,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val level by BubbleLevelBus.level.collectAsState()
    val hud by HudStateBus.state.collectAsState()
    val listening = vm.listening
    val thinking = vm.busy
    val speaking = hud.speaking
    val stateWord = coreStateLabel(listening, thinking, speaking, vm.convoActive)
    val t = rememberInfiniteTransition(label = "harmonic")
    val ripP by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "rip")
    val ringO by t.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "ro")
    val ringI by t.animateFloat(360f, 0f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "ri")
    val lev by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "lev")
    val ent by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "ent")
    val blink by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    val heard = vm.lastHeard.ifBlank { vm.voiceNote.orEmpty() }.ifBlank { "..." }.take(160)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(PremiumNeon.copy(alpha = 0.08f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.7f
                )
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(PremiumNeon.copy(alpha = blink), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "JARVIS.NEURAL // WAKE.01", color = PremiumNeon, fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AUTH: " + vm.masterName.ifBlank { "Master" },
                        color = Color(0xFF6B7280), fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                }
                Text(
                    "MIC " + (level.coerceIn(0f, 1f) * 100).toInt() + "%",
                    color = Color(0xFF6B7280), fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                )
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stateWord, color = Color(0xFFD1D5DB).copy(alpha = 0.5f + 0.5f * blink),
                    fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(48.dp).height(1.dp).background(PremiumNeon))
                Spacer(Modifier.height(40.dp))
                HarmonicCore(ripP, ringO, ringI, lev, ent, level)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "\"" + heard + "\"", color = Color(0xFF9CA3AF), fontSize = 18.sp,
                    fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                PremiumNeon.copy(alpha = 0.3f + 0.5f * level.coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "• " + fmtPingTag(vm.dashPing), color = PremiumNeon, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                    Text(
                        modelShortName(vm.model).uppercase(), color = Color(0xFF6B7280), fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                    Text(
                        voiceTag(vm.hindiListen) + " •", color = Color(0xFF6B7280), fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .premiumGlass(RoundedCornerShape(50))
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMic, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mic",
                            tint = if (listening) Color(0xFFEF4444) else PremiumNeon
                        )
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = Color(0xFF9CA3AF))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF9CA3AF))
                    }
                }
            }
        }
    }
}

private fun DrawScope.nodeDot(radiusPx: Float, angleDeg: Float) {
    val rad = angleDeg * PI.toFloat() / 180f
    val p = center + Offset(cos(rad) * radiusPx, sin(rad) * radiusPx)
    drawCircle(PremiumNeon.copy(alpha = 0.25f), radius = 5.dp.toPx(), center = p)
    drawCircle(Color.White, radius = 2.dp.toPx(), center = p)
}

@Composable
private fun HarmonicCore(ripP: Float, ringO: Float, ringI: Float, lev: Float, ent: Float, level: Float) {
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(360.dp)) {
            for (i in 0..2) {
                val ph = ripplePhase(ripP, i)
                drawCircle(
                    PremiumNeon.copy(alpha = rippleAlpha(ph) * 0.5f),
                    radius = 60.dp.toPx() * rippleScale(ph),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            rotate(ringO, center) {
                nodeDot(130.dp.toPx(), -90f)
                nodeDot(130.dp.toPx(), 40f)
            }
            rotate(ringI, center) {
                nodeDot(90.dp.toPx(), 200f)
                nodeDot(90.dp.toPx(), 320f)
            }
        }
        Box(
            Modifier.offset(y = lev.dp)
                .size(110.dp)
                .background(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.06f),
                        1f to Color.Black.copy(alpha = 0.55f)
                    ),
                    CircleShape
                )
                .border(1.dp, PremiumNeon.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val es = 0.95f + 0.15f * ent + level.coerceIn(0f, 1f) * 0.2f
            Box(
                Modifier.size((34f * es).dp).background(
                    Brush.radialGradient(
                        0f to Color.White,
                        0.35f to Color.White,
                        0.6f to PremiumNeon,
                        1f to Color.Transparent
                    ),
                    CircleShape
                )
            )
        }
    }
}

/**
 * Floating hub bubble: rotated glass diamond + state track ring + glowing
 * core. Drag release spring-snaps to the nearest screen edge, tucked.
 */
@Composable
fun HubBubble(
    modifier: Modifier = Modifier,
    startX: Float = 24f,
    startY: Float = 320f,
    contentWidthDp: Float = 120f,
    listening: Boolean = false,
    speaking: Boolean = false,
    wakeFlash: Boolean = false,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onEdgeRelease: (Int) -> Unit = {},
    dockCmd: Int = 0,
    onDockedChange: (Boolean) -> Unit = {},
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    val state = hubStateFor(listening, speaking, wakeFlash)
    var offsetX by remember { mutableStateOf(startX) }
    var offsetY by remember { mutableStateOf(startY) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val stateNow by rememberUpdatedState(state)
    var dockedSide by remember { mutableStateOf(0) }
    var lastUndockX by remember { mutableStateOf(startX) }
    var lastUndockY by remember { mutableStateOf(startY) }
    var dockJob by remember { mutableStateOf<Job?>(null) }
    var clock by remember { mutableStateOf(0L) }
    var trackDeg by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            clock += 16
            val s = stateNow
            trackDeg = (trackDeg + hubSpinDir(s) * 360f * 16f / hubSpinMs(s)) % 360f
        }
    }
    // Service-driven edge dock (idle hide + wake pop-out).
    LaunchedEffect(dockCmd) {
        val side = dockCmd
        if (side != 0 && dockedSide == 0) {
            val screenWpx = with(density) { configuration.screenWidthDp.dp.toPx() }
            val widthPx = with(density) { contentWidthDp.dp.toPx() }
            val peekPx = with(density) { 30.dp.toPx() }
            val targetX = if (side < 0) -(widthPx - peekPx) else screenWpx - peekPx
            lastUndockX = offsetX
            lastUndockY = offsetY
            dockJob?.cancel()
            dockJob = scope.launch {
                Animatable(offsetX).animateTo(targetX, tween(250)) {
                    offsetX = value
                    onPositionChanged(offsetX, offsetY)
                }
                dockedSide = side
                onDockedChange(true)
            }
        } else if (side == 0 && dockedSide != 0) {
            val tx = lastUndockX
            dockJob?.cancel()
            dockJob = scope.launch {
                Animatable(offsetX).animateTo(tx, tween(250)) {
                    offsetX = value
                    onPositionChanged(offsetX, offsetY)
                }
                offsetY = lastUndockY
                onPositionChanged(offsetX, offsetY)
                dockedSide = 0
                onDockedChange(false)
            }
        }
    }

    val period = hubPeriodMs(state)
    val phase = (clock % period).toFloat() / period
    val (coreS, coreA) = hubPulse(state, phase)
    val popS by animateFloatAsState(
        when (state) {
            HubState.WAKE -> 1.15f
            HubState.LISTENING -> 1.05f
            HubState.IDLE -> 1f
        },
        animationSpec = tween(400, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "hub_pop"
    )
    val coreColor = when (state) {
        HubState.IDLE -> Color(0xFF3B82F6)
        HubState.WAKE -> PremiumNeon
        HubState.LISTENING -> Color.White
    }
    val labelColor = when (state) {
        HubState.IDLE -> Color(0xFFA1A1AA)
        HubState.WAKE -> PremiumNeon
        HubState.LISTENING -> Color.White
    }
    val edgeColor = when (state) {
        HubState.IDLE -> Color(0x1AFFFFFF)
        HubState.WAKE -> PremiumNeon.copy(alpha = 0.4f)
        HubState.LISTENING -> Color(0x4DFFFFFF)
    }
    Column(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dockJob?.cancel()
                        if (dockedSide != 0) {
                            dockedSide = 0
                            onDockedChange(false)
                        }
                        onDragStart()
                    },
                    onDrag = { change, amt ->
                        change.consume()
                        offsetX += amt.x
                        offsetY += amt.y
                        onPositionChanged(offsetX, offsetY)
                    },
                    onDragEnd = {
                        val screenWpx = with(density) { configuration.screenWidthDp.dp.toPx() }
                        val centerPx = with(density) { (contentWidthDp / 2).dp.toPx() }
                        val marginPx = with(density) { 8.dp.toPx() }
                        val edgePx = with(density) { 64.dp.toPx() }
                        val releaseCx = offsetX + centerPx
                        if (releaseCx < edgePx) {
                            onEdgeRelease(-1)
                            return@detectDragGestures
                        }
                        if (releaseCx > screenWpx - edgePx) {
                            onEdgeRelease(1)
                            return@detectDragGestures
                        }
                        val targetX =
                            (if (offsetX + centerPx < screenWpx / 2) marginPx else screenWpx - marginPx) - centerPx
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
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(72.dp).graphicsLayer { scaleX = popS; scaleY = popS }) {
            val diamondTL = center - Offset(27.dp.toPx(), 27.dp.toPx())
            val diamondSize = androidx.compose.ui.geometry.Size(54.dp.toPx(), 54.dp.toPx())
            rotate(45f, center) {
                drawRoundRect(
                    Color(0xCC141419), topLeft = diamondTL, size = diamondSize,
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
                drawRoundRect(
                    edgeColor, topLeft = diamondTL, size = diamondSize,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            rotate(trackDeg, center) {
                drawArc(
                    coreColor.copy(alpha = 0.6f), 0f, 360f, false,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(70.dp.toPx(), 70.dp.toPx()),
                    style = Stroke(
                        2.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = if (state == HubState.LISTENING)
                            PathEffect.dashPathEffect(floatArrayOf(1f, 7.dp.toPx()))
                        else
                            PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))
                    )
                )
            }
            if (state == HubState.LISTENING) {
                drawCircle(
                    Color.White.copy(alpha = (1f - phase) * 0.7f),
                    radius = 11.dp.toPx() * coreS + phase * 15.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            drawCircle(coreColor.copy(alpha = 0.35f * coreA), radius = 11.dp.toPx() * coreS * 1.9f)
            drawCircle(coreColor.copy(alpha = coreA), radius = 11.dp.toPx() * coreS)
            drawCircle(Color.White.copy(alpha = 0.8f * coreA), radius = 11.dp.toPx() * coreS * 0.4f)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            hubLabelFor(listening, speaking, wakeFlash), color = labelColor, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp
        )
    }
}

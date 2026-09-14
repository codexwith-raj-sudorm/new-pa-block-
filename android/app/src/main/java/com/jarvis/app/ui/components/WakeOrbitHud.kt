package com.jarvis.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.BubbleLevelBus
import com.jarvis.app.HudStateBus
import com.jarvis.app.JarvisViewModel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Golden-spiral node layout in the unit circle. Pure, tested. */
fun brainNode(index: Int, count: Int): Pair<Float, Float> {
    if (count <= 1) return 0f to 0f
    val r = kotlin.math.sqrt(index.toFloat() / (count - 1)) * 0.92f
    val a = index * 2.39996f
    return (r * cos(a)) to (r * sin(a))
}

/** Ring + cross-brace edges (index pairs, never self-loops). Pure, tested. */
fun brainEdges(count: Int): List<Pair<Int, Int>> {
    if (count < 3) return emptyList()
    val edges = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until count) edges.add(i to (i + 1) % count)
    for (i in 0 until count / 2) edges.add(i to ((i + count / 2) % count))
    return edges
}

/** Orbit offset for an angle (deg) on an rx/ry ellipse. Pure, tested. */
fun orbitXY(angleDeg: Float, rx: Float, ry: Float): Pair<Float, Float> {
    val a = Math.toRadians(angleDeg.toDouble())
    return (cos(a).toFloat() * rx) to (sin(a).toFloat() * ry)
}

/** Front-ness 0..1 (drives the scale/alpha depth cue). Pure, tested. */
fun orbitDepth(angleDeg: Float): Float =
    (sin(Math.toRadians(angleDeg.toDouble())).toFloat() + 1f) / 2f

/** Panel scale from depth, boosted when focused. Pure, tested. */
fun focusScale(depth: Float, focused: Boolean): Float =
    (0.82f + 0.18f * depth) * (if (focused) 1.22f else 1f)

/**
 * Neural-network brain: golden nodes on cyan threads, signals riding the
 * edges, hot nodes breathing with the live voice level. Tap = interrupt.
 */
@Composable
fun BrainNetwork(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    level: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 170.dp,
    nodeCount: Int = 14
) {
    val flow by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "flow"
    )
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600), repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val threadColor = when {
        speaking -> HudGold
        listening -> HudCyan
        thinking -> HudBlue
        else -> HudCyan.copy(alpha = 0.55f)
    }
    Canvas(modifier.size(sizeDp).clickable(onClick = onTap)) {
        val r = size.minDimension / 2f
        val c = center
        val lvl = level.coerceIn(0f, 1f)
        val pts = List(nodeCount) { i ->
            val (nx, ny) = brainNode(i, nodeCount)
            Offset(c.x + nx * r, c.y + ny * r)
        }
        drawCircle(
            Brush.radialGradient(
                listOf(threadColor.copy(alpha = 0.22f), Color.Transparent),
                center = c, radius = r
            ),
            radius = r, center = c
        )
        val edges = brainEdges(nodeCount)
        for ((a, b) in edges) {
            drawLine(threadColor.copy(alpha = 0.5f), pts[a], pts[b], strokeWidth = 2f)
        }
        for (k in 0 until 3) {
            val f = (flow + k / 3f) % 1f
            val seg = (f * edges.size).toInt().coerceIn(0, edges.size - 1)
            val frac = (f * edges.size) % 1f
            val (a, b) = edges[seg]
            val p = Offset(
                pts[a].x + (pts[b].x - pts[a].x) * frac,
                pts[a].y + (pts[b].y - pts[a].y) * frac
            )
            drawCircle(HudGold.copy(alpha = 0.45f), radius = r * 0.055f, center = p)
            drawCircle(Color.White, radius = r * 0.024f, center = p)
        }
        for (i in pts.indices) {
            val hot = i % 3 == 0
            val grow = 1f + 0.25f * pulse * (if (hot) 1f else 0.4f) +
                0.35f * lvl * (if (hot) 1f else 0f)
            val nr = r * (if (hot) 0.045f else 0.028f) * grow
            drawCircle(HudGold.copy(alpha = 0.3f), nr * 2.2f, pts[i])
            drawCircle(if (hot) HudGold else threadColor, nr, pts[i])
        }
    }
}

private data class OrbitPanel(val title: String, val lines: List<String>, val gold: Boolean)

private data class Placed(val p: OrbitPanel, val pos: Offset, val depth: Float, val i: Int)

@Composable
private fun PanelCard(p: OrbitPanel, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val accent = if (p.gold) HudGold else HudCyan
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1526).copy(alpha = 0.92f))
            .border(
                if (expanded) 2.dp else 1.dp,
                accent.copy(alpha = if (expanded) 0.8f else 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            p.title, color = accent, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        p.lines.forEach { ln ->
            Text(
                ln.ifBlank { "—" }, color = HudInk, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) 6 else 2,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Wake-mode interface: a neural brain core with live panels (voice, heard,
 * reply, system, net) orbiting it. Tap a panel to focus it; fixed controls
 * live at the bottom.
 */
@Composable
fun WakeOrbitHud(
    vm: JarvisViewModel,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val level by BubbleLevelBus.level.collectAsState()
    val hud by HudStateBus.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var focused by remember { mutableStateOf<Int?>(null) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterA by animateFloatAsState(if (entered) 1f else 0f, tween(600), label = "enter")
    val now = remember {
        java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        )
    }
    HudBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val cx = wPx / 2f
            val cy = hPx * 0.40f
            val panelW = with(density) { 144.dp.toPx() }
            val panelH = with(density) { 90.dp.toPx() }
            val rx = ((wPx - panelW) / 2f - with(density) { 8.dp.toPx() }).coerceAtLeast(10f)
            val ry = ((hPx * 0.60f - panelH) / 2f).coerceAtLeast(10f)
            val orbit by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
                label = "orbit"
            )
            val bob by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
                label = "bob"
            )
            Text(
                "WAKE MODE", color = HudGold, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
            )
            val brainTopDp = with(density) { cy.toDp() } - 85.dp
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                BrainNetwork(
                    listening = vm.listening,
                    thinking = vm.busy,
                    speaking = hud.speaking,
                    level = level,
                    onTap = vm::interruptSpeech,
                    modifier = Modifier.padding(top = brainTopDp)
                )
            }
            val lastReply = vm.messages.lastOrNull { it.role != "user" }?.text.orEmpty()
            val batt = if (vm.dashBatt < 0) "—" else "${vm.dashBatt}%"
            val panels = listOf(
                OrbitPanel(
                    "VOICE",
                    listOf(coreStateLabel(vm.listening, vm.busy, hud.speaking, vm.convoActive), "LVL ${(level * 100).toInt()}%"),
                    gold = false
                ),
                OrbitPanel(
                    "HEARD",
                    listOfNotNull(vm.voiceNote, vm.lastHeard.take(120).ifEmpty { null }).ifEmpty { listOf("—") },
                    gold = false
                ),
                OrbitPanel("REPLY", listOf(lastReply.take(160)), gold = true),
                OrbitPanel("SYS", listOf(now, "PWR $batt"), gold = true),
                OrbitPanel("NET", listOf("PING " + vm.dashPing, vm.model.take(20)), gold = false)
            )
            val placed = panels.mapIndexed { i, p ->
                val a = orbit + i * 72f
                val (ox, oy) = orbitXY(a, rx, ry)
                val bobY = sin(bob * 6.283f + i * 1.256f) * 6f
                Placed(p, Offset(cx + ox - panelW / 2f, cy + oy - panelH / 2f + bobY), orbitDepth(a), i)
            }.sortedBy { it.depth }
            for (pl in placed) {
                val isF = focused == pl.i
                val sc = focusScale(pl.depth, isF)
                PanelCard(
                    pl.p,
                    Modifier.offset { IntOffset(pl.pos.x.roundToInt(), pl.pos.y.roundToInt()) }
                        .width(144.dp)
                        .graphicsLayer {
                            scaleX = sc
                            scaleY = sc
                            alpha = (0.72f + 0.28f * pl.depth) * enterA
                        }
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            focused = if (isF) null else pl.i
                        },
                    expanded = isF
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Listening — ends after 10s of quiet",
                    color = Color(0xFF8B949E), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMic) {
                        Icon(
                            if (vm.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mic",
                            tint = if (vm.listening) Color(0xFFE5484D) else HudCyan
                        )
                    }
                    IconButton(onClick = onExpand) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open app", tint = HudCyan)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = HudCyan)
                    }
                }
            }
        }
    }
}

package com.jarvis.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.BubbleLevelBus
import com.jarvis.app.HudStateBus
import com.jarvis.app.JarvisViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Stitch hologram design tokens (neural amber set).
val NeuralHot = Color(0xFFFFF6D6)
val NeuralAmber = Color(0xFFFFD166)
val NeuralGold = Color(0xFFFF9E00)
val NeuralBackdrop = Color(0xFF030710)

/** Fibonacci-lattice point on the unit sphere. Pure, tested. */
fun fibSphere(index: Int, count: Int): Triple<Float, Float, Float> {
    if (count <= 1) return Triple(0f, 1f, 0f)
    val y = 1f - (index.toFloat() / (count - 1)) * 2f
    val r = sqrt((1f - y * y).coerceAtLeast(0f))
    val theta = index * 2.3999632f
    return Triple(r * cos(theta), y, r * sin(theta))
}

/** Rotate a point around the Y axis (deg). Pure, tested. */
fun rotY(p: Triple<Float, Float, Float>, deg: Float): Triple<Float, Float, Float> {
    val a = deg * PI.toFloat() / 180f
    val c = cos(a)
    val s = sin(a)
    return Triple(p.first * c + p.third * s, p.second, -p.first * s + p.third * c)
}

/** Rotate a point around the X axis (deg). Pure, tested. */
fun rotX(p: Triple<Float, Float, Float>, deg: Float): Triple<Float, Float, Float> {
    val a = deg * PI.toFloat() / 180f
    val c = cos(a)
    val s = sin(a)
    return Triple(p.first, p.second * c - p.third * s, p.second * s + p.third * c)
}

/** Perspective scale for a z in -1..1. Pure, tested. */
fun projectScale(z: Float, persp: Float = 3f): Float = persp / (persp - z)

/** Front-ness 0 (rear) .. 1 (front) from a z in -1..1. Pure, tested. */
fun sphereDepth(z: Float): Float = ((z + 1f) / 2f).coerceIn(0f, 1f)

/** Index pairs closer than threshold (each pair once, never self). Pure, tested. */
fun sphereNeighbors(count: Int, threshold: Float = 0.5f): List<Pair<Int, Int>> {
    if (count < 2) return emptyList()
    val pts = List(count) { fibSphere(it, count) }
    val out = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until count) {
        for (j in i + 1 until count) {
            val a = pts[i]
            val b = pts[j]
            val dx = a.first - b.first
            val dy = a.second - b.second
            val dz = a.third - b.third
            if (dx * dx + dy * dy + dz * dz <= threshold * threshold) out.add(i to j)
        }
    }
    return out
}

/** Full-revolution time (ms) per voice state. Pure, tested. */
fun holoSpinMs(listening: Boolean, thinking: Boolean, speaking: Boolean): Int = when {
    thinking -> 2500
    speaking -> 4500
    listening -> 7000
    else -> 7000
}

/** Decorative frequency readout wobbling with the mic level. Pure, tested. */
fun fmtFreq(level: Float): String =
    "FREQ: %.1f Hz".format(432.8f + level.coerceIn(0f, 1f) * 36f)

/** Ping tag for the telemetry deck. Pure, tested. */
fun fmtPingTag(ping: String): String =
    ping.uppercase().replace(" ", "").ifBlank { "—" }

/** Model tag for the telemetry deck. Pure, tested. */
fun modelTag(model: String): String =
    model.substringAfter("/").take(16).uppercase().ifBlank { "—" }

/** Voice-language tag. Pure, tested. */
fun voiceTag(hindi: Boolean): String = if (hindi) "VOICE • HI" else "VOICE • EN"

/** Static dotted grid + corner brackets (skips recomposition — no params). */
@Composable
private fun HoloBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 26f
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.05f), radius = 1f, center = Offset(x, y))
                x += step
            }
            y += step
        }
        val br = NeuralGold.copy(alpha = 0.55f)
        val inset = 16f
        val len = 30f
        val w = size.width
        val h = size.height
        drawLine(br, Offset(inset, inset + len), Offset(inset, inset), 2f)
        drawLine(br, Offset(inset, inset), Offset(inset + len, inset), 2f)
        drawLine(br, Offset(w - inset - len, inset), Offset(w - inset, inset), 2f)
        drawLine(br, Offset(w - inset, inset), Offset(w - inset, inset + len), 2f)
        drawLine(br, Offset(inset, h - inset - len), Offset(inset, h - inset), 2f)
        drawLine(br, Offset(inset, h - inset), Offset(inset + len, h - inset), 2f)
        drawLine(br, Offset(w - inset - len, h - inset), Offset(w - inset, h - inset), 2f)
        drawLine(br, Offset(w - inset, h - inset), Offset(w - inset, h - inset - len), 2f)
    }
}

/**
 * The Stitch neural hologram: rotating fibonacci sphere, synaptic filaments,
 * tilted dashed latitude rings, degree ring, amber glow. Tap = interrupt.
 */
@Composable
private fun NeuralSphere(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    level: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nodeCount = 90
    val pts3 = remember(nodeCount) { List(nodeCount) { fibSphere(it, nodeCount) } }
    val pairs = remember(nodeCount) { sphereNeighbors(nodeCount) }
    val spinMs = holoSpinMs(listening, thinking, speaking)
    key(spinMs) {
        val spin by rememberInfiniteTransition().animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(spinMs, easing = LinearEasing)),
            label = "spin"
        )
        val flow by rememberInfiniteTransition().animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "flow"
        )
        val lvl = level.coerceIn(0f, 1f)
        Canvas(modifier.size(300.dp).clickable(onClick = onTap)) {
            val c = center
            val ringR = size.minDimension / 2f * 0.93f
            val tighten = if (thinking) 0.88f else 1f
            val r = size.minDimension / 2f * 0.62f * tighten * (1f + 0.09f * lvl)
            // Amber glow blob, low-right of the core.
            val glowAt = c + Offset(r * 0.9f, r * 1.15f)
            drawCircle(
                Brush.radialGradient(
                    listOf(NeuralGold.copy(alpha = 0.32f), Color.Transparent),
                    center = glowAt, radius = r * 1.7f
                ),
                radius = r * 1.7f, center = glowAt
            )
            // Degree ring.
            drawCircle(NeuralGold.copy(alpha = 0.22f), ringR, c, style = Stroke(1.5f))
            // Tilted dashed latitude rings.
            val dash = PathEffect.dashPathEffect(floatArrayOf(15f, 25f, 40f, 15f))
            rotate(-15f, c) {
                scale(1f, 0.32f, c) {
                    drawCircle(
                        NeuralAmber.copy(alpha = 0.35f), r * 1.04f, c,
                        style = Stroke(1.5f, pathEffect = dash)
                    )
                }
            }
            rotate(12f, c) {
                scale(1f, 0.5f, c) {
                    drawCircle(
                        NeuralGold.copy(alpha = 0.3f), r * 0.82f, c,
                        style = Stroke(1.2f, pathEffect = dash)
                    )
                }
            }
            // Projected nodes.
            val proj = pts3.map { p ->
                val spun = rotX(rotY(p, spin), 22f)
                val k = projectScale(spun.third)
                val d = sphereDepth(spun.third)
                Triple(Offset(c.x + spun.first * k * r, c.y + spun.second * k * r), d, k)
            }
            // Synaptic filaments behind the nodes.
            for ((a, b) in pairs) {
                val pa = proj[a]
                val pb = proj[b]
                val ad = (pa.second + pb.second) / 2f
                drawLine(
                    NeuralAmber.copy(alpha = 0.10f + 0.30f * ad),
                    pa.first, pb.first, strokeWidth = 1f + ad
                )
            }
            // Speaking shockwaves.
            if (speaking) {
                for (k in 0 until 2) {
                    val f = (flow + k / 2f) % 1f
                    drawCircle(
                        NeuralHot.copy(alpha = (1f - f) * 0.45f),
                        r * (0.7f + f * 0.8f), c, style = Stroke(2f)
                    )
                }
            }
            // Nodes: rear dim gold, front hot white.
            for ((o, d, _) in proj) {
                val nr = r * 0.026f * (0.7f + 0.6f * d)
                if (d > 0.55f) drawCircle(NeuralAmber.copy(alpha = 0.22f * d), nr * 2.6f, o)
                drawCircle(
                    lerp(NeuralGold.copy(alpha = 0.55f), NeuralHot, d),
                    nr, o
                )
            }
        }
    }
}

/**
 * Wake-mode interface, Stitch hologram edition: telemetry header, state
 * readout, neural sphere, heard echo, level bar, data tags, controls.
 */
@Composable
fun NeuralHologramHud(
    vm: JarvisViewModel,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val level by BubbleLevelBus.level.collectAsState()
    val hud by HudStateBus.state.collectAsState()
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterA by animateFloatAsState(if (entered) 1f else 0f, tween(500), label = "enter")
    val listening = vm.listening
    val thinking = vm.busy
    val speaking = hud.speaking
    val state = coreStateLabel(listening, thinking, speaking, vm.convoActive)
    val heard = vm.lastHeard.ifBlank { vm.voiceNote.orEmpty() }.ifBlank { "…" }.take(140)
    Box(
        Modifier.fillMaxSize().background(NeuralBackdrop)
    ) {
        HoloBackdrop()
        Column(
            Modifier.fillMaxSize().padding(top = 20.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "● JARVIS.NEURAL // WAKE.01",
                    color = NeuralGold.copy(alpha = 0.8f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                Text(
                    fmtFreq(level),
                    color = NeuralGold.copy(alpha = 0.8f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
            Text(
                state,
                color = NeuralAmber, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 4.sp, modifier = Modifier.padding(top = 64.dp)
            )
            Box(
                Modifier.padding(top = 6.dp).width(34.dp).height(2.dp)
                    .background(NeuralAmber.copy(alpha = 0.9f))
            )
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                    NeuralSphere(
                        listening = listening, thinking = thinking,
                        speaking = speaking, level = level * enterA,
                        onTap = vm::interruptSpeech
                    )
                    Text(
                        "000°", color = NeuralGold.copy(alpha = 0.6f),
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    )
                    Text(
                        "180°", color = NeuralGold.copy(alpha = 0.6f),
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                    Text(
                        "270°", color = NeuralGold.copy(alpha = 0.6f),
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp)
                    )
                    Text(
                        "090°", color = NeuralGold.copy(alpha = 0.6f),
                        fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)
                    )
                }
            }
            Text(
                "“" + heard + "”",
                color = NeuralHot, fontSize = 15.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            )
            Box(Modifier.padding(top = 14.dp).width(220.dp).height(3.dp)) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.White.copy(alpha = 0.12f))
                )
                Box(
                    Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).height(3.dp)
                        .background(
                            Brush.horizontalGradient(listOf(NeuralGold, NeuralHot))
                        )
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, start = 32.dp, end = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "● " + fmtPingTag(vm.dashPing),
                    color = NeuralGold, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    modelTag(vm.model),
                    color = NeuralAmber.copy(alpha = 0.7f), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    voiceTag(vm.hindiListen) + " ●",
                    color = NeuralGold, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMic) {
                    Icon(
                        if (listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mic",
                        tint = if (listening) Color(0xFFE5484D) else NeuralAmber
                    )
                }
                IconButton(onClick = onExpand) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open app", tint = NeuralAmber)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NeuralAmber)
                }
            }
        }
    }
}

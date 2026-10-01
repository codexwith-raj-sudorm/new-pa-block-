package com.jarvis.app.frontend.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * §10 neural-link gate: OLED lock screen + glass auth module + 3-phase
 * establish sequence. Local identity (per house rule) wearing the OAuth
 * prototype's terminal aesthetic. First run only.
 */
@Composable
fun AuthGate(clearance: String, onDone: (String) -> Unit) {
    var phase by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    val finalName = name.ifBlank { "Master" }

    LaunchedEffect(phase) {
        if (phase == 2) {
            delay(2500)
            phase = 3
        } else if (phase == 3) {
            delay(2200)
            onDone(finalName)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .drawBehind {
                // Cyber grid: 20dp lines at 2% white.
                val step = 20.dp.toPx()
                val c = Color.White.copy(alpha = 0.02f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(c, Offset(x, 0f), Offset(x, size.height))
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(c, Offset(0f, y), Offset(size.width, y))
                    y += step
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Green ambient light from the top.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cx = constraints.maxWidth / 2f
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to PremiumNeon.copy(alpha = 0.1f),
                        0.7f to Color.Transparent,
                        center = Offset(cx, -100f)
                    )
                )
            )
        }
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(24.dp)
                .shadow(30.dp, RoundedCornerShape(32.dp))
                .background(Color(0xB30C0C0F), RoundedCornerShape(32.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.95f)) togetherWith
                        (fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.95f))
                },
                label = "auth_phase"
            ) { p ->
                when (p) {
                    1 -> AuthLogin(name, { name = it.take(40) }) { phase = 2 }
                    2 -> AuthLinking()
                    else -> AuthConfirmed(finalName, clearance)
                }
            }
        }
    }
}

@Composable
private fun AuthLogin(name: String, onName: (String) -> Unit, onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp)
                .shadow(20.dp, RoundedCornerShape(16.dp))
                .background(Color.Black, RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(12.dp)
                    .shadow(10.dp, CircleShape, ambientColor = PremiumNeon, spotColor = PremiumNeon)
                    .background(PremiumNeon, CircleShape)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Jarvis", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Establish a secure neural link to wake your personal intelligence.",
            color = Color(0xFF9CA3AF), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = onName,
            placeholder = { Text("Your name, sir?") },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PremiumNeon.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = PremiumNeon
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF131314), contentColor = Color(0xFFE3E3E3)
            ),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFF8E918F)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
        ) {
            Box(Modifier.size(10.dp).background(PremiumNeon, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text("Establish Neural Link", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AuthLinking() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = PremiumNeon,
            trackColor = Color.White.copy(alpha = 0.1f),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "ESTABLISHING NEURAL LINK", color = PremiumNeon, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "VERIFYING LOCAL IDENTITY...", color = Color(0xFF6B7280), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
    }
}

@Composable
private fun AuthConfirmed(name: String, clearance: String) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(1500), label = "auth_prog")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp)
                    .shadow(30.dp, CircleShape, ambientColor = PremiumNeon.copy(alpha = 0.3f), spotColor = PremiumNeon.copy(alpha = 0.3f))
                    .background(PremiumNeon, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(avatarLetter(name), color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)
                    .size(24.dp)
                    .background(Color.Black, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(10.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "CLEARANCE: $clearance", color = Color(0xFF9CA3AF), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.Black, CircleShape)) {
            Box(
                Modifier.fillMaxWidth(progress).height(4.dp)
                    .background(PremiumNeon, CircleShape)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "INITIALIZING NEXUS ARRAY...", color = Color(0xFF6B7280), fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
    }
}

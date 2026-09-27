package com.jarvis.app.frontend.screens

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.backend.system.BubbleLevelBus
import com.jarvis.app.backend.system.HudStateBus
import com.jarvis.app.backend.system.sharedJarvisVm
import com.jarvis.app.frontend.design.ArcCoreReactor
import com.jarvis.app.frontend.design.coreStateLabel
import kotlinx.coroutines.delay

/**
 * Default-assistant overlay: when Jarvis is the device's default assistant,
 * the system hold-gesture (home / power) lands here. Floats over the current
 * app — tap outside to dismiss.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = sharedJarvisVm(application as Application)
        setContent {
            val tick by HudStateBus.ticker.collectAsState()
            val level by BubbleLevelBus.level.collectAsState()
            val hud by HudStateBus.state.collectAsState()
            LaunchedEffect(tick) {
                if (tick?.text == "[CONVO: END]") {
                    delay(1500)
                    finish()
                }
            }
            LaunchedEffect(Unit) {
                vm.refreshDashboard()
                vm.startConvoSession()
            }
            LaunchedEffect(vm.captureHideTick) {
                if (vm.captureHideTick > 0) {
                    try {
                        moveTaskToBack(true)
                    } catch (_: Exception) {
                    }
                }
            }
            val listening = vm.listening
            val thinking = vm.busy
            val speaking = hud.speaking
            val state = coreStateLabel(listening, thinking, speaking, vm.convoActive)
            val heard = vm.lastHeard.ifBlank { vm.voiceNote.orEmpty() }.ifBlank { "…" }.take(300)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { finish() }
                )
                Column(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color(0xFF0B1220), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .clickable(onClick = {})
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArcCoreReactor(
                            listening, thinking, speaking, level,
                            onTap = {
                                if (vm.listening) vm.stopListening()
                                else {
                                    try {
                                        vm.startListening(fromUser = true)
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            sizeDp = 64.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "JARVIS // ASSIST",
                                color = Color(0xFFFBBF24), fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                state,
                                color = Color(0xFFF59E0B), fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                            )
                            Text(
                                heard,
                                color = Color(0xFFE2E8F0), fontSize = 13.sp,
                                maxLines = 5, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.askAboutScreen() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("ASK ABOUT SCREEN", color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { openJarvis() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
                        ) {
                            Text("OPEN", color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    private fun openJarvis() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (_: Exception) {
        }
        finish()
    }
}

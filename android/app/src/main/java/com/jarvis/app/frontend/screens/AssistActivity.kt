package com.jarvis.app.frontend.screens

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.jarvis.app.backend.system.HudStateBus
import com.jarvis.app.backend.system.sharedJarvisVm
import com.jarvis.app.frontend.design.NeuralHologramHud
import kotlinx.coroutines.delay

/**
 * Default-assistant overlay: when Jarvis is the device's default assistant,
 * the system hold-gesture (home / power) lands here.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = sharedJarvisVm(application as Application)
        setContent {
            val tick by HudStateBus.ticker.collectAsState()
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
            Box(Modifier.fillMaxSize()) {
                NeuralHologramHud(
                    vm,
                    onMic = {
                        if (vm.listening) vm.stopListening()
                        else {
                            try {
                                vm.startListening(fromUser = true)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onExpand = { openJarvis() },
                    onClose = { finish() }
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
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
                        Text("OPEN JARVIS", color = Color.White, fontFamily = FontFamily.Monospace)
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

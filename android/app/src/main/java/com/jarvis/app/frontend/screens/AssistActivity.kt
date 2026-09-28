package com.jarvis.app.frontend.screens

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.app.backend.system.HudStateBus
import com.jarvis.app.backend.system.sharedJarvisVm
import com.jarvis.app.frontend.design.AssistIslandSheet
import com.jarvis.app.frontend.design.EdgeFlashOverlay
import com.jarvis.app.frontend.design.coreStateLabel
import kotlinx.coroutines.delay

/**
 * Default-assistant overlay: when Jarvis is the device's default assistant,
 * the system hold-gesture (home / power) lands here. Neon island sheet over
 * the current app — tap outside or swipe down to dismiss.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = sharedJarvisVm(application as Application)
        setContent {
            val tick by HudStateBus.ticker.collectAsState()
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
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { finish() }
                )
                EdgeFlashOverlay(fireTick = 0)
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    AssistIslandSheet(
                        stateLabel = state,
                        heard = heard,
                        listening = listening,
                        thinking = thinking,
                        onCoreTap = {
                            if (vm.listening) vm.stopListening()
                            else {
                                try {
                                    vm.startListening(fromUser = true)
                                } catch (_: Exception) {
                                }
                            }
                        },
                        onAskScreen = { vm.askAboutScreen() },
                        onOpen = { openJarvis() },
                        onClose = { finish() }
                    )
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

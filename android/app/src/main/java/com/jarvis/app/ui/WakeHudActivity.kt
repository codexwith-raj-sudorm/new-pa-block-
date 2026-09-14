package com.jarvis.app.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jarvis.app.HudStateBus
import com.jarvis.app.MainActivity
import com.jarvis.app.sharedJarvisVm
import com.jarvis.app.ui.components.WakeOrbitHud
import kotlinx.coroutines.delay

/** Full-screen wake-mode interface: orbiting HUD over the shared ViewModel. */
class WakeHudActivity : ComponentActivity() {
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
            WakeOrbitHud(
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
                onExpand = {
                    try {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    } catch (_: Exception) {
                    }
                    finish()
                },
                onClose = { finish() }
            )
        }
    }
}

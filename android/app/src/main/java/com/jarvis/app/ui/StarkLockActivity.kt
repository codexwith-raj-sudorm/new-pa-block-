package com.jarvis.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class StarkLockActivity : FragmentActivity() {
    private var lockMsg by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1220)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STARK INDUSTRIES // SECURITY GATE",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ACCESS RESTRICTED // RETINAL SCAN REQUIRED",
                        color = Color(0xFFFEF3C7),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { authenticateUser() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                    ) {
                        Text("INITIATE BIOMETRIC SCAN", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    if (lockMsg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(lockMsg, color = Color(0xFFFCA5A5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        authenticateUser()
    }

    private fun authenticateUser() {
        if (BiometricManager.from(this).canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS) {
            lockMsg = "Biometrics unavailable on this device"
            return
        }
        lockMsg = ""
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                finish() // Unlock app and return to main HUD
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                lockMsg = errString.toString()
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                lockMsg = "Not recognized — try again"
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Stark Neural Identity Check")
            .setSubtitle("Confirm identity to access classified JARVIS vault")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

1. build.gradle.kts (App-level)
The dependency catalog locks in the latest Compose Bill of Materials (2026.09.00), Material 3, and Coroutines, ensuring the AI does not hallucinate outdated UI components.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15" //
    }
}

dependencies {
    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00") //
    implementation(composeBom)
    
    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Android Lifecycle & Activity
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Networking (For Phase 5 - Zero-Cost LLM)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

2. res/xml/assistant_service_config.xml
Registers the system bindings required for Android to recognize the app as a default digital assistant.
<?xml version="1.0" encoding="utf-8"?>
<voice-interaction-service 
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="com.jarvis.app.service.JarvisVoiceSessionService"
    android:recognitionService="com.jarvis.app.service.JarvisRecognitionService"
    android:supportsAssist="true"
    android:supportsLocalInteraction="true" />

3. AndroidManifest.xml
Declares the required BIND_VOICE_INTERACTION permissions, making the service hookable via the Android system UI.
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.jarvis.app">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Jarvis"
        android:theme="@android:style/Theme.Translucent.NoTitleBar">

        <!-- Phase 1: Background Interaction Service -->
        <service
            android:name=".service.JarvisVoiceInteractionService"
            android:permission="android.permission.BIND_VOICE_INTERACTION"
            android:exported="true">
            <meta-data
                android:name="android.voice_interaction"
                android:resource="@xml/assistant_service_config" />
            <intent-filter>
                <action android:name="android.service.voice.VoiceInteractionService" />
            </intent-filter>
        </service>

        <!-- Phase 1: Foreground Session Service (UI Container) -->
        <service
            android:name=".service.JarvisVoiceSessionService"
            android:permission="android.permission.BIND_VOICE_INTERACTION"
            android:exported="true" />

        <!-- Temporary activity to launch Android Assistant Settings -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>

4. JarvisVoiceInteractionService.kt
The persistent background service the Android OS keeps alive to handle hardware triggers.
package com.jarvis.app.service

import android.service.voice.VoiceInteractionService
import android.util.Log

class JarvisVoiceInteractionService : VoiceInteractionService() {
    
    override fun onReady() {
        super.onReady()
        Log.d("JarvisService", "VoiceInteractionService is bound and ready.")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d("JarvisService", "VoiceInteractionService shutting down.")
    }
}

5. JarvisVoiceSessionService.kt
The factory class Android calls to generate a new UI session window when a user swipes the corner or long-presses the power button.
package com.jarvis.app.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class JarvisVoiceSessionService : VoiceInteractionSessionService() {
    
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceSession(this)
    }
}

6. JarvisVoiceSession.kt
The active visual window. Currently inflates a placeholder Compose UI. Phase 2 will replace this with the 3D Neural Matrix.
package com.jarvis.app.service

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView

class JarvisVoiceSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x92030710)), // MatrixBackdrop (92% opacity dark navy)
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "JARVIS SYSTEM HOOK ACTIVE", color = Color(0xFFFFD166))
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Request visual context for Phase 3
        setUiEnabled(true)
    }

    override fun onHide() {
        super.onHide()
        // Cleanup resources
    }
}

To complete Phase 1, install the app, open Settings > Apps > Default Apps > Digital Assistant app, and select Jarvis. Long-pressing the power button will display the transparent JARVIS SYSTEM HOOK ACTIVE overlay.

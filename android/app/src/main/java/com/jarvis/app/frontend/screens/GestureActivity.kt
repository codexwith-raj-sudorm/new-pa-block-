package com.jarvis.app.frontend.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.app.backend.gesture.GestureCameraSession
import com.jarvis.app.backend.gesture.GestureEvent
import com.jarvis.app.backend.gesture.ModelDownloadProgress
import com.jarvis.app.backend.gesture.NormalizedPoint
import com.jarvis.app.backend.gesture.ScreenPoint
import com.jarvis.app.backend.system.AccessBridge
import com.jarvis.app.backend.system.isAccessEnabled
import kotlin.math.roundToInt

private val GestureGreen = Color(0xFF7CFFB2)
private val GesturePanel = Color(0xE6141B2D)

private fun formatDataSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024f
    if (kb < 1024f) return "${"%.0f".format(kb)} KB"
    return "${"%.1f".format(kb / 1024f)} MB"
}

private data class GestureUiState(
    val status: String = "Starting local gesture control…",
    val modelProgress: ModelDownloadProgress? = null,
    val modelReady: Boolean = false,
    val pointer: NormalizedPoint? = null,
    val label: String? = null,
    val confidence: Float = 0f,
    val error: String? = null,
    val actionCount: Int = 0,
)

/** Visible, user-started gesture-control screen. */
class GestureActivity : ComponentActivity() {
    private var uiState = mutableStateOf(GestureUiState())
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private val sessionListener = object : GestureCameraSession.Listener {
        override fun onStatus(text: String) {
            uiState.value = uiState.value.copy(status = text, error = null)
        }

        override fun onModelProgress(progress: ModelDownloadProgress) {
            uiState.value = uiState.value.copy(modelProgress = progress, modelReady = false)
        }

        override fun onModelReady(bytes: Long) {
            uiState.value = uiState.value.copy(
                modelProgress = ModelDownloadProgress(bytes, bytes),
                modelReady = true,
                status = "Installing local gesture module…",
            )
        }

        override fun onPointer(point: NormalizedPoint) {
            uiState.value = uiState.value.copy(pointer = point)
        }

        override fun onObservation(label: String?, confidence: Float) {
            uiState.value = uiState.value.copy(label = label, confidence = confidence)
        }

        override fun onAction(event: GestureEvent, screenPoint: ScreenPoint?) {
            when (event) {
                GestureEvent.Click -> {
                    if (!isAccessEnabled(this@GestureActivity)) {
                        uiState.value = uiState.value.copy(
                            status = "Closed fist detected — enable Screen control to allow taps",
                        )
                        return
                    }
                    val ok = screenPoint != null && AccessBridge.dispatchTap(screenPoint.x, screenPoint.y)
                    uiState.value = uiState.value.copy(
                        status = if (ok) "Tap dispatched" else "Tap was rejected by Screen control",
                        actionCount = if (ok) uiState.value.actionCount + 1 else uiState.value.actionCount,
                    )
                }
                GestureEvent.Confirm -> {
                    uiState.value = uiState.value.copy(
                        status = "Thumbs up detected — no pending action to confirm",
                    )
                }
                is GestureEvent.PointerMoved -> Unit
            }
        }

        override fun onError(text: String) {
            uiState.value = uiState.value.copy(status = "Gesture control stopped", error = text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                uiState.value = uiState.value.copy(
                    status = "Camera permission is required for gesture control",
                    error = "Permission denied",
                )
            }
            recreate()
        }
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                GestureScreen(
                    state = uiState.value,
                    cameraGranted = hasCameraPermission(),
                    onRequestCamera = { requestCameraPermission() },
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onBack = { finish() },
                    listener = sessionListener,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }
}

@Composable
private fun GestureScreen(
    state: GestureUiState,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onBack: () -> Unit,
    listener: GestureCameraSession.Listener,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
        if (!cameraGranted) {
            PermissionPanel(onBack = onBack, onRequestCamera = onRequestCamera)
        } else {
            val cameraView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
            DisposableEffect(lifecycleOwner, cameraView) {
                previewView = cameraView
                val session = GestureCameraSession(context, lifecycleOwner, listener)
                session.start(cameraView)
                onDispose {
                    previewView = null
                    session.close()
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { cameraView },
                    modifier = Modifier.fillMaxSize(),
                )
                PointerOverlay(state.pointer)
                GestureTopBar(onBack = onBack)
                GestureStatusPanel(
                    state = state,
                    onOpenAccessibility = onOpenAccessibility,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun PermissionPanel(onBack: () -> Unit, onRequestCamera: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = GestureGreen, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text("Gesture Control", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Jarvis uses the camera locally to track your hand. The session is visible and can be stopped at any time.",
            color = Color(0xFFB6C2D9), fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRequestCamera,
            colors = ButtonDefaults.buttonColors(containerColor = GestureGreen, contentColor = Color.Black),
        ) { Text("Allow camera") }
    }
}

@Composable
private fun GestureTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Stop gesture control", tint = Color.White)
        }
        Text("GESTURE CONTROL", color = Color.White, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Text("LIVE", color = GestureGreen, fontSize = 11.sp)
    }
}

@Composable
private fun PointerOverlay(point: NormalizedPoint?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (point != null) {
            val x = ((1f - point.x).coerceIn(0f, 1f) * constraints.maxWidth).roundToInt()
            val y = (point.y.coerceIn(0f, 1f) * constraints.maxHeight).roundToInt()
            Box(
                Modifier.offset { IntOffset(x - 14, y - 14) }
                    .size(28.dp)
                    .alpha(0.9f)
                    .background(GestureGreen.copy(alpha = 0.75f), CircleShape),
            )
        }
    }
}

@Composable
private fun GestureStatusPanel(
    state: GestureUiState,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(16.dp).fillMaxWidth()
            .background(GesturePanel, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = GestureGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(state.status, color = Color.White, fontSize = 13.sp)
        }
        state.modelProgress?.let { progress ->
            val fraction = progress.fraction
            if (!state.modelReady) {
                LinearProgressIndicator(
                    progress = { fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = GestureGreen,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
            }
            Text(
                if (fraction != null && progress.totalBytes != null) {
                    "Data required: ${formatDataSize(progress.totalBytes)} • " +
                        "${formatDataSize(progress.downloadedBytes)} downloaded (${(fraction * 100f).roundToInt()}%)"
                } else {
                    "Data downloaded: ${formatDataSize(progress.downloadedBytes)} • total size from server unavailable"
                },
                color = Color(0xFFB6C2D9),
                fontSize = 11.sp,
            )
        }
        state.error?.let { Text(it, color = Color(0xFFFF9B9B), fontSize = 12.sp) }
        Text(
            "Pose: ${state.label ?: "No hand"}  ${(state.confidence * 100f).roundToInt()}%  •  Taps: ${state.actionCount}",
            color = Color(0xFFB6C2D9), fontSize = 11.sp,
        )
        Text(
            "Open palm moves the pointer. Hold a closed fist to tap. Camera frames stay on this device.",
            color = Color(0xFF8F9BB2), fontSize = 11.sp,
        )
        OutlinedButton(
            onClick = onOpenAccessibility,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) { Text("Enable Screen control", fontSize = 12.sp) }
    }
}

package com.jarvis.gesturelab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Constraints
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

private val LabGreen = Color(0xFF7CFFB2)
private val LabPanel = Color(0xE6141B2D)

private data class LabUiState(
    val status: String = "Starting motion lab…",
    val modelProgress: ModelDownloadProgress? = null,
    val modelReady: Boolean = false,
    val pointer: NormalizedPoint? = null,
    val label: String? = null,
    val confidence: Float = 0f,
    val rotation: Int = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val error: String? = null,
)

class GestureLabActivity : ComponentActivity() {
    private var state = mutableStateOf(LabUiState())
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    private val listener = object : GestureCameraSession.Listener {
        override fun onStatus(text: String) {
            state.value = state.value.copy(status = text, error = null)
        }

        override fun onModelProgress(progress: ModelDownloadProgress) {
            state.value = state.value.copy(modelProgress = progress, modelReady = false)
        }

        override fun onModelReady(bytes: Long) {
            state.value = state.value.copy(
                modelProgress = ModelDownloadProgress(bytes, bytes),
                modelReady = true,
                status = "Loading local gesture module…",
            )
        }

        override fun onFrameInfo(rotationDegrees: Int, width: Int, height: Int) {
            state.value = state.value.copy(
                rotation = rotationDegrees,
                frameWidth = width,
                frameHeight = height,
            )
        }

        override fun onPointer(point: NormalizedPoint) {
            state.value = state.value.copy(pointer = point)
        }

        override fun onObservation(label: String?, confidence: Float) {
            state.value = state.value.copy(label = label, confidence = confidence)
        }

        override fun onError(text: String) {
            state.value = state.value.copy(status = "Motion lab stopped", error = text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                state.value = state.value.copy(
                    status = "Camera permission is required for the motion lab",
                    error = "Permission denied",
                )
            }
            recreate()
        }
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                MotionLabScreen(
                    state = state.value,
                    cameraGranted = hasCameraPermission(),
                    onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBack = { finish() },
                    listener = listener,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
private fun MotionLabScreen(
    state: LabUiState,
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onBack: () -> Unit,
    listener: GestureCameraSession.Listener,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    Surface(Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
        if (!cameraGranted) {
            PermissionPanel(onBack, onRequestCamera)
        } else {
            val cameraView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
            DisposableEffect(lifecycleOwner, cameraView) {
                val session = GestureCameraSession(context, lifecycleOwner, listener)
                session.start(cameraView)
                onDispose { session.close() }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView({ cameraView }, Modifier.fillMaxSize())
                PointerOverlay(state.pointer)
                TopBar(onBack)
                StatusPanel(state, Modifier.align(Alignment.BottomCenter))
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
        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = LabGreen, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text("Gesture Motion Lab", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "A standalone local test app for hand tracking. It does not connect to Jarvis or dispatch taps.",
            color = Color(0xFFB6C2D9), fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onRequestCamera,
            colors = ButtonDefaults.buttonColors(containerColor = LabGreen, contentColor = Color.Black),
        ) { Text("Allow camera") }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Stop motion lab", tint = Color.White)
        }
        Text("MOTION LAB", color = Color.White, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Text("LOCAL ONLY", color = LabGreen, fontSize = 11.sp)
    }
}

@Composable
private fun PointerOverlay(point: NormalizedPoint?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (point != null) {
            val x = (point.x.coerceIn(0f, 1f) * constraints.maxWidth).roundToInt()
            val y = (point.y.coerceIn(0f, 1f) * constraints.maxHeight).roundToInt()
            Box(
                Modifier.offset { IntOffset(x - 14, y - 14) }
                    .size(28.dp)
                    .alpha(0.9f)
                    .background(LabGreen.copy(alpha = 0.75f), CircleShape),
            )
        }
    }
}

@Composable
private fun StatusPanel(state: LabUiState, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(16.dp).fillMaxWidth()
            .background(LabPanel, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = LabGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(state.status, color = Color.White, fontSize = 13.sp)
        }
        state.modelProgress?.let { progress ->
            val fraction = progress.fraction
            if (!state.modelReady) {
                LinearProgressIndicator(
                    progress = { fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = LabGreen,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
            }
            Text(
                if (fraction != null && progress.totalBytes != null) {
                    "Data required: ${formatDataSize(progress.totalBytes)} • " +
                        "${formatDataSize(progress.downloadedBytes)} downloaded (${(fraction * 100f).roundToInt()}%)"
                } else {
                    "Data downloaded: ${formatDataSize(progress.downloadedBytes)} • total unavailable"
                },
                color = Color(0xFFB6C2D9), fontSize = 11.sp,
            )
        }
        state.error?.let { Text(it, color = Color(0xFFFF9B9B), fontSize = 12.sp) }
        Text(
            "Pose: ${state.label ?: "No hand"}  ${(state.confidence * 100f).roundToInt()}%",
            color = Color(0xFFB6C2D9), fontSize = 11.sp,
        )
        Text(
            "Rotation: ${state.rotation}°  •  Frame: ${state.frameWidth}×${state.frameHeight}",
            color = Color(0xFFB6C2D9), fontSize = 11.sp,
        )
        Text(
            "Open palm and pointing movement are visualized only. No Jarvis actions are connected in this app.",
            color = Color(0xFF8F9BB2), fontSize = 11.sp,
        )
    }
}

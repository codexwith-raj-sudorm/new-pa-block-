package com.jarvis.app.frontend.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.jarvis.app.R
import com.jarvis.app.backend.system.HudState
import com.jarvis.app.backend.system.HudStateBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Lightweight companion states. The camera/MediaPipe gesture module is not
 * involved; these states are driven by Jarvis' existing voice/brain signals.
 */
enum class JarvisShimejiState {
    LISTENING,
    SPEAKING,
    WALKING,
    FALLING,
    PROCESSING,
    EXECUTING,
    SLEEP,
    RECOVER,
    ERROR,
}

private val idleFrames = intArrayOf(R.drawable.jarvis_frame_1)
private val speakingFrames = intArrayOf(R.drawable.jarvis_frame_2, R.drawable.jarvis_frame_3)
private val fallingFrames = intArrayOf(R.drawable.jarvis_frame_9, R.drawable.jarvis_frame_10)
private val processingFrames = intArrayOf(R.drawable.jarvis_frame_11)
private val executingFrames = intArrayOf(R.drawable.jarvis_frame_13, R.drawable.jarvis_frame_14)
private val errorFrames = intArrayOf(R.drawable.jarvis_frame_16)
private val sleepFrames = intArrayOf(R.drawable.jarvis_frame_12)
private val recoverFrames = intArrayOf(R.drawable.jarvis_frame_15)

private fun framesFor(state: JarvisShimejiState): IntArray = when (state) {
    JarvisShimejiState.LISTENING -> idleFrames
    JarvisShimejiState.SPEAKING,
    JarvisShimejiState.WALKING -> speakingFrames
    JarvisShimejiState.FALLING -> fallingFrames
    JarvisShimejiState.PROCESSING -> processingFrames
    JarvisShimejiState.EXECUTING -> executingFrames
    JarvisShimejiState.SLEEP -> sleepFrames
    JarvisShimejiState.RECOVER -> recoverFrames
    JarvisShimejiState.ERROR -> errorFrames
}

/** Maps the existing Jarvis HUD state to a companion animation state. */
fun shimejiStateFor(hud: HudState): JarvisShimejiState = when {
    !hud.online -> JarvisShimejiState.ERROR
    hud.thinking -> JarvisShimejiState.PROCESSING
    hud.listening -> JarvisShimejiState.LISTENING
    hud.speaking -> JarvisShimejiState.SPEAKING
    else -> JarvisShimejiState.LISTENING
}

/**
 * Reusable sprite animation. It resets at every state transition and only
 * advances when that state has multiple frames, keeping idle work minimal.
 */
@Composable
fun JarvisShimeji(
    currentState: JarvisShimejiState,
    modifier: Modifier = Modifier,
) {
    val activeFrames = remember(currentState) { framesFor(currentState) }
    var currentFrameIndex by remember(currentState) { mutableIntStateOf(0) }

    LaunchedEffect(currentState, activeFrames) {
        currentFrameIndex = 0
        if (activeFrames.size > 1) {
            while (isActive) {
                delay(300)
                currentFrameIndex = (currentFrameIndex + 1) % activeFrames.size
            }
        }
    }

    Image(
        painter = painterResource(activeFrames[currentFrameIndex]),
        contentDescription = "J.A.R.V.I.S. companion",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Full-screen content for the transparent system overlay window. */
@Composable
fun JarvisShimejiOverlay(modifier: Modifier = Modifier) {
    val hud by HudStateBus.state.collectAsState()
    Box(modifier = modifier.fillMaxSize()) {
        JarvisShimeji(
            currentState = shimejiStateFor(hud),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

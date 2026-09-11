package com.jarvis.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun StarkGoldenBubble(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    var offsetX by remember { mutableStateOf(100f) }
    var offsetY by remember { mutableStateOf(300f) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(72.dp) // FR-17: 72dp size specification
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onPositionChanged(offsetX, offsetY)
                }
            }
    ) {
        // Embeds the 3D Golden Neural Matrix inside the draggable boundary
        GoldenBrainCoreView(
            sizeDp = 72.dp,
            onClick = onClick
        )
    }
}

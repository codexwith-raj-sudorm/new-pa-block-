package com.jarvis.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Cyber Mode: Linux terminal palette ---
val CyberBlack = Color(0xFF000000)
val CyberPanel = Color(0xFF040704)
val CyberGreen = Color(0xFF00FF41)
val CyberDim = Color(0xFF00B32B)
val CyberPale = Color(0xFFB8FFC4)
val CyberAmber = Color(0xFFFFB000)
val CyberRed = Color(0xFFFF3131)

/** Picks the chat backdrop by mode: phosphor terminal vs fluid glow. */
@Composable
fun ThemedBackground(cyber: Boolean, content: @Composable BoxScope.() -> Unit) {
    if (cyber) CyberTerminalBackground(content) else FluidAnimatedBackground(content)
}

/** Black CRT backdrop: scanlines + a faint green glow from the top edge. */
@Composable
fun CyberTerminalBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberPanel)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyberGreen.copy(alpha = 0.07f), Color.Transparent),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width / 2f, 0f)
                )
                var y = 0f
                val step = 4.dp.toPx()
                while (y < size.height) {
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.5f)
                    y += step
                }
            }
    ) {
        content()
    }
}

/** One chat line as terminal output: `$ input` in green, `▸ output` in pale phosphor. */
@Composable
fun CyberMessageLine(text: String, isUser: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Text(
            if (isUser) "$ " else "▸ ",
            color = if (isUser) CyberGreen else CyberDim,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text,
            color = if (isUser) CyberGreen else CyberPale,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Terminal input bar: `$` prompt, green block cursor, bracket [ SEND ] / [ MIC ] key. */
@Composable
fun CyberInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    isListening: Boolean,
    onMicTap: () -> Unit,
    micEnabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasText = text.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberBlack)
            .drawBehind {
                drawLine(CyberDim, Offset(0f, 0f), Offset(size.width, 0f), 2f)
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (isListening) {
            Text(
                "● REC -- listening… (tap [ STOP ])",
                color = CyberRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$ ",
                color = CyberGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                textStyle = TextStyle(
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(CyberGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (hasText) {
                        onSend(text)
                        keyboardController?.hide()
                    }
                }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "enter command…",
                                color = CyberDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .border(1.dp, if (hasText || isListening) CyberGreen else CyberDim)
                    .clickable(enabled = hasText || micEnabled) {
                        if (hasText) {
                            onSend(text)
                            keyboardController?.hide()
                        } else {
                            onMicTap()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    if (hasText) "[ SEND ]" else if (isListening) "[ STOP ]" else "[ MIC ]",
                    color = if (hasText || isListening) CyberGreen else CyberDim,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Login banner + blinking-block prompt, shown while the chat is empty. */
@Composable
fun CyberBootBanner(user: String = "master") {
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "blink"
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        cyberBootLines().forEach { line ->
            Text(
                line,
                color = CyberDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cyberPrompt(user) + " ",
                color = CyberGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                "█",
                color = CyberGreen.copy(alpha = 0.15f + 0.85f * blink),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

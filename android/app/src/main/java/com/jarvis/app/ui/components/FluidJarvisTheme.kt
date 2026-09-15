package com.jarvis.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// --- New Fluid AI Color Palette ---
val FluidBgDark = Color(0xFF05050A)
val FluidGlowPrimary = Color(0xFF4318FF) // Deep Violet
val FluidGlowSecondary = Color(0xFF00E5FF) // Electric Blue
val GlassPanel = Color(0x25FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val TextPrimary = Color(0xFFF0F0F5)
val TextSecondary = Color(0xFFA0A0AB)

/**
 * 1. Animated Glowing Background
 * Replaces the static backdrop with a slowly rotating, glowing gradient mesh.
 */
@Composable
fun FluidAnimatedBackground(content: @Composable BoxScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg_rotate_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FluidBgDark)
    ) {
        // Rotating glow blob
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(FluidGlowPrimary.copy(alpha = 0.4f), Color.Transparent),
                            radius = size.width * 0.8f
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width / 4, size.height / 4)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(FluidGlowSecondary.copy(alpha = 0.3f), Color.Transparent),
                            radius = size.width * 0.9f
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f)
                    )
                }
        )
        // Content container
        content()
    }
}

/**
 * 2. Glassmorphic Chat Bubble with Entrance Animations
 */
@Composable
fun AnimatedGlassBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    // Entrance animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50) // Slight delay for stagger effect
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        ) + fadeIn(tween(300)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    ))
                    .background(if (isUser) FluidGlowPrimary.copy(alpha = 0.7f) else GlassPanel)
                    .border(1.dp, GlassBorder, RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 20.dp
                    ))
                    .padding(16.dp)
            ) {
                Text(
                    text = text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * 3. Fluid Input Floating Pill
 * A morphing, floating input bar that responds to typing and listening states.
 */
@Composable
fun FluidInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    isListening: Boolean,
    onMicTap: () -> Unit,
    micEnabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasText = text.isNotBlank()

    // Animate the button scaling
    val buttonScale by animateFloatAsState(
        targetValue = if (hasText || isListening) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f), label = "btn_scale"
    )

    Surface(
        color = GlassPanel,
        shape = CircleShape,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding() // Keep it above system navigation
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Invisible TextField
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { 
                    if (hasText) { onSend(text); keyboardController?.hide() } 
                }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("Message JARVIS...", color = TextSecondary, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            // Animated Mic / Send Button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (hasText) listOf(FluidGlowPrimary, FluidGlowSecondary)
                            else if (isListening) listOf(Color(0xFFFF2A5F), Color(0xFFFF7B00))
                            else listOf(GlassPanel, GlassPanel)
                        )
                    )
                    .clickable(enabled = hasText || micEnabled) {
                        if (hasText) {
                            onSend(text)
                            keyboardController?.hide()
                        } else {
                            onMicTap()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = hasText, label = "icon_crossfade") { showSend ->
                    Icon(
                        imageVector = if (showSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                        contentDescription = "Action",
                        tint = if (hasText || isListening) Color.White else TextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .drawBehind { scale(buttonScale, buttonScale) }
                    )
                }
            }
        }
    }
}

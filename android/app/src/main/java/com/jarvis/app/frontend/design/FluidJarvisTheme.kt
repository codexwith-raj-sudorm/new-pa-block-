package com.jarvis.app.frontend.design

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// --- New Fluid AI Color Palette ---
val FluidGlowPrimary = Color(0xFF4318FF) // Deep Violet
val GlassPanel = Color(0x25FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val TextPrimary = Color(0xFFF0F0F5)


/**
 * Glassmorphic chat bubble with entrance animations
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

package com.jarvis.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeaderMiniReactor(
    isSpeaking: Boolean,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier
) {
    // FR-6 & FR-8: Shown iff HudStateBus.state.speaking == true; no layout shifts
    AnimatedVisibility(
        visible = isSpeaking,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            // FR-7: 40dp reactor core; tap cuts off speech immediately
            GoldenBrainCoreView(
                sizeDp = 40.dp,
                onClick = onInterrupt
            )
            Spacer(modifier = Modifier.width(6.dp))
            // FR-7: Text hint (screen-reader accessible per E5)
            Text(
                text = "TAP TO STOP",
                color = Color(0xFFFBBF24),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

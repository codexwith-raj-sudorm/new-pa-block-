package com.jarvis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StarkBriefingDashboard(
    temperature: String,
    batteryLevel: Int,
    systemPing: String,
    onRefresh: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color(0xFF0F172A).copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable { onRefresh() }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "⟳ PROTOCOL DELTA // MORNING BRIEFING",
                color = Color(0xFFFBBF24),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "• Weather Telemetry: $temperature", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Text(text = "• System Battery: " + (if (batteryLevel < 0) "—" else "$batteryLevel%"), color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Text(text = "• Network Latency: $systemPing", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "[STATUS: ALL SYSTEMS NOMINAL]",
                color = Color(0xFF10B981),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

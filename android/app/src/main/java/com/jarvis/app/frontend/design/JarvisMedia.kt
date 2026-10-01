package com.jarvis.app.frontend.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.backend.brain.MEDIA_PREF_SYSTEM
import com.jarvis.app.backend.brain.mediaAppLabel

/** §11 playback app chooser: tap a row to play, optionally remembered. */
@Composable
fun MediaChooserDialog(
    query: String,
    manage: Boolean,
    choices: List<String>,
    onPick: (String?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var remember by remember { mutableStateOf(false) }
    JarvisGlassDialog(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (manage) "Playback app" else "Play with…",
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
                if (!manage) {
                    Text(
                        "“" + query.take(60) + "”",
                        color = PremiumMuted, fontSize = 13.sp, maxLines = 1
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (pkg in choices) {
                MediaAppRow(pkg, remember, manage) { onPick(pkg, if (manage) true else remember) }
            }
            MediaAppRow(MEDIA_PREF_SYSTEM, remember, manage, system = true) {
                onPick(MEDIA_PREF_SYSTEM, if (manage) true else remember)
            }
        }
        if (!manage) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remember choice", color = Color(0xFFD1D5DB), fontSize = 13.sp)
                NeonToggle(remember) { remember = !remember }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun MediaAppRow(
    pkg: String,
    remember: Boolean,
    manage: Boolean,
    system: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (system) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (system) Color(0xFF9CA3AF) else PremiumNeon,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            mediaAppLabel(pkg) + if (!manage && remember) " (always)" else "",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
        )
    }
}

package com.jarvis.app.frontend.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Clearance label for the identity card. Pure, tested. */
fun clearanceLabel(masterInstalled: Boolean, baked: Boolean): String = when {
    baked && masterInstalled -> "OWNER"
    masterInstalled -> "ADMIN"
    else -> "GUEST"
}

/**
 * Floating header: hamburger circle, center status pill, profile avatar.
 * Wake + voice toggles live in the drawer; speaking turns the pill into a stop switch.
 */
@Composable
fun JarvisHeader(
    online: Boolean,
    wakeOn: Boolean,
    speaking: Boolean,
    avatarLetter: String,
    onMenu: () -> Unit,
    onAvatar: () -> Unit,
    onStatusTap: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuPositioned: (LayoutCoordinates) -> Unit = {},
    onStatusPositioned: (LayoutCoordinates) -> Unit = {},
    onAvatarPositioned: (LayoutCoordinates) -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp)
                .onGloballyPositioned(onMenuPositioned)
                .premiumGlass(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color(0xFFD1D5DB), modifier = Modifier.size(14.dp))
        }
        Column(
            Modifier.onGloballyPositioned(onStatusPositioned)
                .premiumGlass(RoundedCornerShape(50))
                .clickable(onClick = onStatusTap)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("J.A.R.V.I.S", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (speaking) "TAP TO STOP" else hudStatusLine(online, wakeOn),
                color = if (speaking) JarvisRed else PremiumNeon,
                fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
        }
        Box(
            Modifier.size(40.dp)
                .onGloballyPositioned(onAvatarPositioned)
                .shadow(8.dp, CircleShape, ambientColor = PremiumNeon.copy(alpha = 0.15f), spotColor = PremiumNeon.copy(alpha = 0.15f))
                .clip(CircleShape)
                .background(PremiumNeon.copy(alpha = 0.1f))
                .border(1.dp, PremiumNeon.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center
        ) {
            Text(avatarLetter, color = PremiumNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .border(
                1.dp,
                if (active) Color.White.copy(alpha = 0.05f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (active) Color(0xFF9CA3AF) else Color(0xFF6B7280),
            modifier = Modifier.size(20.dp)
        )
        Text(
            label, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (active) Color.White else Color(0xFF9CA3AF)
        )
    }
}

/** Slide-in navigation drawer: 75% frosted panel with create button + sections + toggles. */
@Composable
fun NavDrawerContent(
    drawerState: DrawerState,
    ttsOn: Boolean,
    wakeOn: Boolean,
    onNewChat: () -> Unit,
    onChats: () -> Unit,
    onMemory: () -> Unit,
    onList: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleWake: () -> Unit
) {
    val scope = rememberCoroutineScope()
    fun go(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }
    Box(
        Modifier.fillMaxHeight()
            .fillMaxWidth(0.75f)
            .widthIn(max = 300.dp)
            .background(Color(0xF20A0A0C))
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(8.dp).background(PremiumNeon, CircleShape))
                Text(
                    "NAVIGATION", color = Color(0xFF9CA3AF), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { go(onNewChat) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumNeon, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create New Chat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
            DrawerRow(Icons.Filled.Message, "Recent Chats", active = true) { go(onChats) }
            DrawerRow(Icons.Filled.Memory, "Memory Hub") { go(onMemory) }
            DrawerRow(Icons.Filled.List, "Saved Lists") { go(onList) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
            DrawerRow(
                if (ttsOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                if (ttsOn) "Voice on" else "Voice off"
            ) { go(onToggleTts) }
            DrawerRow(Icons.Filled.Mic, if (wakeOn) "Wake on" else "Wake off") { go(onToggleWake) }
        }
        Box(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )
    }
}

@Composable
private fun HubRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD1D5DB))
    }
}

/** Profile + settings hub: scrim + card blooming from the top-right avatar. */
@Composable
fun ProfileHubOverlay(
    show: Boolean,
    name: String,
    clearance: String,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = show, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(200)) + scaleIn(tween(250), transformOrigin = TransformOrigin(1f, 0f)),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), transformOrigin = TransformOrigin(1f, 0f))
    ) {
        Box(
            Modifier.fillMaxSize().padding(top = 76.dp, end = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Column(
                Modifier.width(288.dp)
                    .jarvisGlass(RoundedCornerShape(16.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        Modifier.size(48.dp).background(PremiumNeon, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            avatarLetter(name), color = Color.Black,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(10.dp))
                            Text(
                                clearance, color = Color(0xFF9CA3AF), fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                Column(Modifier.padding(8.dp)) {
                    HubRow(Icons.Filled.Edit, "Edit Profile", onEditProfile)
                    HubRow(Icons.Filled.Settings, "System Settings", onSettings)
                }
            }
        }
    }
}

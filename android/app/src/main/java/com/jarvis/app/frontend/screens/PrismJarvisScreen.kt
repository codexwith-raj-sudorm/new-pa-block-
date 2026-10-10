package com.jarvis.app.frontend.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.backend.brain.ChatMessage
import com.jarvis.app.backend.brain.JarvisViewModel
import com.jarvis.app.backend.system.sharedJarvisVm

private val PrismCanvas = Color(0xFFFAFAFC)
private val PrismInk = Color(0xFF1E293B)
private val PrismMuted = Color(0xFF64748B)
private val PrismLine = Color(0xFFE7EAF0)
private val PrismRainbow = Brush.linearGradient(
    listOf(Color(0xFFFF5E62), Color(0xFFFF9966), Color(0xFFF9D423), Color(0xFF00CFE8), Color(0xFF4FACFE), Color(0xFF9B51E0))
)
private const val DESIGN_PREFS = "jarvis_ui_preferences"
private const val DESIGN_KEY = "home_design"
private const val DESIGN_LEGACY = "legacy"
private const val DESIGN_PRISM = "prism"

/**
 * Design switch wrapper. The existing Jarvis HUD is preserved as Legacy, while
 * the Prism screen is an alternate home surface backed by the same ViewModel.
 */
@Composable
fun JarvisScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(DESIGN_PREFS, Context.MODE_PRIVATE) }
    var design by remember { mutableStateOf(preferences.getString(DESIGN_KEY, DESIGN_LEGACY) ?: DESIGN_LEGACY) }
    var chooserOpen by remember { mutableStateOf(false) }

    fun choose(next: String) {
        preferences.edit().putString(DESIGN_KEY, next).apply()
        design = next
        chooserOpen = false
    }

    Box(Modifier.fillMaxSize()) {
        if (design == DESIGN_PRISM) {
            PrismHome(onOpenDesigns = { chooserOpen = true })
        } else {
            LegacyJarvisScreen()
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 92.dp)) {
            DesignPickerButton(design = design, onClick = { chooserOpen = true })
        }
    }
    if (chooserOpen) {
        DesignChooserDialog(current = design, onPick = ::choose, onDismiss = { chooserOpen = false })
    }
}

@Composable
private fun DesignPickerButton(design: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(Color.Black.copy(alpha = if (design == DESIGN_PRISM) 0.04f else 0.58f), RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.textButtonColors(contentColor = if (design == DESIGN_PRISM) Color(0xFF475569) else Color.White),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Filled.SwapHoriz, contentDescription = "Choose design", modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (design == DESIGN_PRISM) "Prism design" else "Classic design", fontSize = 10.sp)
    }
}

@Composable
private fun DesignChooserDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Jarvis design") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DesignOption("Classic HUD", "The existing Jarvis glass / neon interface", current == DESIGN_LEGACY) { onPick(DESIGN_LEGACY) }
                DesignOption("Prism workspace", "Clean white, rainbow accents, dashboard layout", current == DESIGN_PRISM) { onPick(DESIGN_PRISM) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun DesignOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = PrismMuted)
        }
    }
}

@Composable
private fun PrismHome(onOpenDesigns: () -> Unit) {
    val context = LocalContext.current
    val vm: JarvisViewModel = remember { sharedJarvisVm(context.applicationContext as android.app.Application) }
    var search by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val rainbowSoft = Brush.linearGradient(
        listOf(Color(0x14FF5E62), Color(0x14F9D423), Color(0x1400CFE8), Color(0x149B51E0))
    )

    Column(Modifier.fillMaxSize().background(PrismCanvas)) {
        PrismHeader(vm = vm, search = search, onSearch = { search = it }, onDesigns = onOpenDesigns)
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The Prism HTML's ingestion banner becomes a Jarvis command banner.
            Box(
                Modifier.fillMaxWidth().border(2.dp, PrismRainbow, RoundedCornerShape(24.dp)).background(Color.White, RoundedCornerShape(24.dp)).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Jarvis workspace", color = PrismInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("A clean command center for your existing Jarvis tools.", color = PrismMuted, fontSize = 12.sp)
                        }
                        Text("LOCAL HUD", color = Color(0xFF0F766E), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFECFDF5), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 5.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PrismInput(prompt, { prompt = it }, "Ask Jarvis anything…", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { if (prompt.isNotBlank()) { vm.send(prompt.trim()); prompt = "" } },
                            colors = ButtonDefaults.buttonColors(containerColor = PrismInk),
                            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)
                        ) { Text("Send", fontSize = 12.sp) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrismInfoCard("01", "System status", if (vm.brainOk) "Online and ready" else "Offline tools ready", Icons.Filled.CheckCircle, Modifier.weight(1f))
                PrismInfoCard("02", "Wake word", if (vm.wakeOn) "Listening enabled" else "Currently off", Icons.Filled.Mic, Modifier.weight(1f))
            }

            Text("Quick actions", color = PrismInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrismAction("New chat", Icons.Filled.Add) { vm.newChat() }
                PrismAction("Chats", Icons.Filled.ChatBubbleOutline) { vm.showChats = true }
                PrismAction("Memory", Icons.Filled.School) { vm.showMemory = true }
                PrismAction("Settings", Icons.Filled.Settings) { vm.openSettings() }
            }

            Box(Modifier.fillMaxWidth().background(rainbowSoft, RoundedCornerShape(20.dp)).border(1.dp, PrismLine, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(PrismRainbow, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Recent conversation", color = PrismInk, fontWeight = FontWeight.Bold)
                    }
                    val messages = vm.messages.takeLast(3)
                    if (messages.isEmpty()) {
                        Text("Start a conversation and your Jarvis responses will appear here.", color = PrismMuted, fontSize = 13.sp)
                    } else {
                        messages.forEach { PrismMessage(it) }
                    }
                }
            }
            Spacer(Modifier.height(92.dp))
        }
    }
}

@Composable
private fun PrismHeader(vm: JarvisViewModel, search: String, onSearch: (String) -> Unit, onDesigns: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.94f)).border(1.dp, PrismLine).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).background(PrismRainbow, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Jarvis", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text("JARVIS", color = PrismInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.width(6.dp))
        Text("PRISM", color = Color(0xFF9B51E0), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        PrismInput(search, onSearch, "Search Jarvis…", Modifier.weight(1f))
        Spacer(Modifier.width(7.dp))
        TextButton(onClick = onDesigns, contentPadding = PaddingValues(horizontal = 5.dp)) {
            Icon(Icons.Filled.GridView, contentDescription = "Designs", tint = PrismMuted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun PrismInput(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value, onValueChange = onChange, modifier = modifier.background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)).border(1.dp, PrismLine, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
        singleLine = true, textStyle = TextStyle(color = PrismInk, fontSize = 13.sp), cursorBrush = SolidColor(Color(0xFF4FACFE)),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Color(0xFF94A3B8), fontSize = 13.sp); inner() } }
    )
}

@Composable
private fun PrismInfoCard(number: String, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, PrismLine, RoundedCornerShape(16.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(number, color = Color(0xFF0284C7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(icon, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(16.dp))
        }
        Text(title, color = PrismInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(value, color = PrismMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrismAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PrismLine), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)) {
        Icon(icon, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = PrismInk, fontSize = 12.sp)
    }
}

@Composable
private fun PrismMessage(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Text(
            message.text.take(220), color = if (isUser) Color.White else PrismInk, fontSize = 12.sp,
            modifier = Modifier.background(if (isUser) Color(0xFF334155) else Color.White, RoundedCornerShape(12.dp)).border(1.dp, PrismLine, RoundedCornerShape(12.dp)).padding(10.dp)
        )
    }
}

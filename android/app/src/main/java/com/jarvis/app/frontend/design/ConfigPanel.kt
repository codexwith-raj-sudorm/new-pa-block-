package com.jarvis.app.frontend.design

import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jarvis.app.backend.ai.AI_GEMINI
import com.jarvis.app.backend.ai.AI_OPENAI
import com.jarvis.app.backend.brain.JarvisViewModel
import com.jarvis.app.backend.brain.Models
import com.jarvis.app.backend.data.MASTER_SELF_NAME
import com.jarvis.app.backend.system.defaultAssistantSettingsIntent
import com.jarvis.app.backend.system.isJarvisDefaultAssistant
import kotlinx.coroutines.delay

/** Header network subtitle. Pure, tested. */
fun configNetLabel(online: Boolean): String = if (online) "Network Secure" else "Offline Mode"

/** Security card title by install state. Pure, tested. */
fun masterCardTitle(installed: Boolean): String =
    if (installed) "Master Mode Active" else "Install Master Key"

/** GitHub status badge: explicit status wins, else master-backed token. Pure, tested. */
fun githubBadge(status: String, masterInstalled: Boolean): String? = when {
    status.isNotBlank() -> status
    masterInstalled -> "✓ Active via Master"
    else -> null
}

/** Mono dark input (single- or multi-line). */
@Composable
fun ConfigInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (focused) PremiumNeon.copy(alpha = 0.6f) else Color(0x1AFFFFFF),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        cursorBrush = SolidColor(PremiumNeon),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color.White.copy(alpha = 0.25f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                inner()
            }
        }
    )
}

@Composable
private fun SegmentTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .background(
                if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Color(0xFF9CA3AF)
        )
    }
}

/** Gemini Core | Custom API segmented control. */
@Composable
fun SegmentedTabs(provider: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        SegmentTab("Gemini Core", provider == AI_GEMINI, Modifier.weight(1f)) { onPick(AI_GEMINI) }
        SegmentTab("Custom API", provider == AI_OPENAI, Modifier.weight(1f)) { onPick(AI_OPENAI) }
    }
}

/** Neon toggle switch. */
@Composable
fun NeonToggle(checked: Boolean, onToggle: () -> Unit) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, tween(300), label = "knob")
    Box(
        Modifier.size(44.dp, 24.dp)
            .background(
                if (checked) PremiumNeon.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                CircleShape
            )
            .border(
                1.dp,
                if (checked) PremiumNeon.copy(alpha = 0.5f) else Color(0x1AFFFFFF),
                CircleShape
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.offset(x = (2 + knob * 20).dp)
                .size(18.dp)
                .background(if (checked) PremiumNeon else Color(0xFFA1A1AA), CircleShape)
        )
    }
}

@Composable
private fun ConfigSection(title: String) {
    Column {
        Text(
            title, color = Color(0xFF6B7280), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)))
    }
}

@Composable
private fun NeonGhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = PremiumNeon.copy(alpha = 0.1f), contentColor = PremiumNeon
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumNeon.copy(alpha = 0.2f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RedGhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val red = Color(0xFFF87171)
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = red.copy(alpha = 0.1f), contentColor = red
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, red.copy(alpha = 0.2f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Security clearance card (visible only after the 5-tap unlock). */
@Composable
fun MasterClearanceCard(vm: JarvisViewModel) {
    var mkey by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var imp by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .border(1.dp, PremiumNeon.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(masterCardTitle(vm.masterInstalled), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (!vm.masterInstalled) {
            Text("Enter your master key — identity loads automatically.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
            ConfigInput(mkey, { mkey = it.trim() }, "Choose a master key (4+ chars)", password = true)
            NeonPillButton("Install master key", Modifier.fillMaxWidth()) {
                vm.installMaster(mkey, MASTER_SELF_NAME, "")
                mkey = ""
            }
            ConfigInput(imp, { imp = it.trim() }, "...or paste a master card to import", singleLine = false, maxLines = 2)
            NeonGhostButton("Import master card", { vm.importMasterCard(imp); imp = "" }, Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(PremiumNeon, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Identity Confirmed: " + vm.masterName.ifBlank { "Master" },
                    color = Color(0xFFD1D5DB), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (vm.isBakedMaster) {
                Text(
                    "Baked identity — revoking needs no key.",
                    color = Color(0xFF9CA3AF), fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RedGhostButton("Revoke Key", { vm.removeMasterBaked() }, Modifier.weight(1f))
                    NeonGhostButton("Export Card", { vm.shareMasterCard() }, Modifier.weight(1f))
                }
            } else {
                ConfigInput(confirm, { confirm = it.trim() }, "Current key to remove", password = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RedGhostButton("Revoke Key", { if (vm.removeMaster(confirm)) confirm = "" }, Modifier.weight(1f))
                    NeonGhostButton("Export Card", { vm.shareMasterCard() }, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * System configuration bottom sheet: security clearance, neural engine,
 * integrations, hardware gestures. Replaces the old settings dialog.
 */
@Composable
fun ConfigPanel(vm: JarvisViewModel) {
    val setCtx = LocalContext.current
    var masterTaps by remember { mutableStateOf(0) }
    var key by remember { mutableStateOf(vm.apiKey) }
    var gh by remember { mutableStateOf(vm.githubToken) }
    val models = vm.availableModels.toList().ifEmpty { Models.FALLBACK }
    var model by remember { mutableStateOf(vm.model) }
    var provider by remember { mutableStateOf(vm.aiProvider) }
    var oaiKey by remember { mutableStateOf(vm.openaiKey) }
    var oaiBase by remember { mutableStateOf(vm.openaiBase) }
    var oaiModel by remember { mutableStateOf(vm.openaiModel) }
    var gestureOn by remember { mutableStateOf(isJarvisDefaultAssistant(setCtx)) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(models.joinToString()) {
        if (model !in models && models.isNotEmpty()) model = models[0]
    }
    LaunchedEffect(Unit) {
        entered = true
        while (true) {
            delay(1000)
            gestureOn = isJarvisDefaultAssistant(setCtx)
        }
    }
    val enterOff by animateDpAsState(
        if (entered) 0.dp else 20.dp,
        tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)), label = "cfg_in"
    )
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(400), label = "cfg_a")
    fun openGestureSettings() {
        try {
            setCtx.startActivity(defaultAssistantSettingsIntent())
        } catch (_: Exception) {
            Toast.makeText(setCtx, "Couldn't open assistant settings", Toast.LENGTH_SHORT).show()
        }
    }
    Dialog(
        onDismissRequest = { vm.showSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            Column(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .offset(y = enterOff)
                    .graphicsLayer { alpha = enterAlpha }
                    .background(Color(0xBF0F0F12), sheetShape)
                    .border(1.dp, Color(0x14FFFFFF), sheetShape)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(32.dp)
                            .background(PremiumNeon.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, PremiumNeon.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier.weight(1f).clickable {
                            if (!vm.masterUnlocked) {
                                masterTaps++
                                if (masterTaps >= 5) {
                                    vm.setMasterUnlocked()
                                    Toast.makeText(setCtx, "Master section unlocked", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("System Config", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            configNetLabel(vm.brainOk), color = PremiumNeon, fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                        )
                    }
                    IconButton(onClick = { vm.showSettings = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF9CA3AF))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)))
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    if (vm.masterUnlocked) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ConfigSection("SECURITY CLEARANCE")
                            MasterClearanceCard(vm)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfigSection("NEURAL ENGINE")
                        SegmentedTabs(provider) { provider = it }
                        if (provider == AI_OPENAI) {
                            Text(
                                "Supports OpenAI-compatible endpoints (Groq, xAI, DeepSeek, Ollama). Fails over to Gemini automatically.",
                                color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.Light
                            )
                            ConfigInput(oaiKey, { oaiKey = it.trim() }, "Other-AI Key (sk-...)", password = true)
                            ConfigInput(oaiBase, { oaiBase = it.trim() }, "Base URL (Blank = OpenAI)")
                            ConfigInput(oaiModel, { oaiModel = it.trim() }, "Model (e.g. gpt-4o-mini)")
                        } else if (key.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { vm.refreshModels(key) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Refresh models", fontSize = 12.sp)
                                }
                            }
                            Text("Preferred model (auto-falls-back on quota):", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                            models.forEach { m ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (model == m) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { model = m }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = model == m, onClick = { model = m })
                                    Text(
                                        m, color = Color.White, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfigSection("INTEGRATIONS")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gemini API Key (Optional)", color = Color(0xFFD1D5DB), fontSize = 11.sp)
                            ConfigInput(key, { key = it.trim() }, "Paste your key (AIza...)", password = true)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("GitHub PAT (Repo Access)", color = Color(0xFFD1D5DB), fontSize = 11.sp)
                                val badge = githubBadge(vm.githubStatus(), vm.masterInstalled)
                                if (badge != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(badge, color = PremiumNeon, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            ConfigInput(gh, { gh = it.trim() }, "github_pat_...", password = true)
                            Text(
                                "Tokens are encrypted locally. Read-only permissions required.",
                                color = Color(0xFF6B7280), fontSize = 10.sp
                            )
                        }
                        if (vm.settingsMsg.isNotBlank()) {
                            Text(vm.settingsMsg, color = PremiumNeon, fontSize = 12.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfigSection("HARDWARE INTERFACE")
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Hold-Gesture Override", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("JARVIS answers physical device triggers", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                            }
                            NeonToggle(gestureOn) { openGestureSettings() }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GhostPillButton("Abort", Modifier.weight(1f)) { vm.showSettings = false }
                    NeonPillButton(
                        "Commit Changes",
                        Modifier.weight(1f)
                    ) {
                        vm.saveSettings(key, model, provider, oaiKey, oaiBase, oaiModel)
                        vm.saveGithubToken(gh)
                    }
                }
            }
        }
    }
}

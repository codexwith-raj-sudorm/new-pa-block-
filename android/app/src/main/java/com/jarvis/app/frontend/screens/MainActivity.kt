package com.jarvis.app.frontend.screens

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import com.jarvis.app.frontend.screens.StarkShareActivity
import com.jarvis.app.frontend.design.AcousticArray
import com.jarvis.app.frontend.design.ArcCoreReactor
import com.jarvis.app.frontend.design.HeaderMiniReactor
import com.jarvis.app.frontend.design.HudCyan
import com.jarvis.app.frontend.design.HudGold
import com.jarvis.app.frontend.design.HudInk
import com.jarvis.app.frontend.design.coreStateLabel
import com.jarvis.app.frontend.design.hudReadoutLine
import com.jarvis.app.frontend.design.hudStatusLine
import com.jarvis.app.frontend.design.AnimatedGlassBubble
import com.jarvis.app.frontend.design.CyberBootBanner
import com.jarvis.app.frontend.design.CyberDim
import com.jarvis.app.frontend.design.CyberGreen
import com.jarvis.app.frontend.design.CyberInputBar
import com.jarvis.app.frontend.design.CyberMessageLine
import com.jarvis.app.frontend.design.CyberPanel
import com.jarvis.app.frontend.design.FluidAnimatedBackground
import com.jarvis.app.frontend.design.FluidInputBar
import com.jarvis.app.frontend.design.ThemedBackground
import com.jarvis.app.frontend.design.HudDialog
import com.jarvis.app.frontend.design.HudTextField
import com.jarvis.app.frontend.widgets.StarkWidgetProvider
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarvis.app.backend.ai.AI_GEMINI
import com.jarvis.app.backend.ai.AI_OPENAI
import com.jarvis.app.backend.brain.ChatData
import com.jarvis.app.backend.brain.ChatMessage
import com.jarvis.app.backend.brain.JarvisViewModel
import com.jarvis.app.backend.brain.Models
import com.jarvis.app.backend.brain.codeShareText
import com.jarvis.app.backend.brain.formatBriefing
import com.jarvis.app.backend.brain.splitCodeBlocks
import com.jarvis.app.backend.data.MASTER_SELF_NAME
import com.jarvis.app.backend.data.dueText
import com.jarvis.app.backend.device.StarkSounds
import com.jarvis.app.backend.system.BubbleLevelBus
import com.jarvis.app.backend.system.defaultAssistantPkg
import com.jarvis.app.backend.system.defaultAssistantSettingsIntent
import com.jarvis.app.backend.system.isJarvisDefaultAssistant
import com.jarvis.app.backend.system.HudStateBus
import com.jarvis.app.backend.system.InterruptBus
import com.jarvis.app.backend.system.WakeService
import com.jarvis.app.backend.system.isAccessEnabled
import com.jarvis.app.backend.system.sharedJarvisVm
import com.jarvis.app.frontend.widgets.ACTION_WIDGET_TAP
import com.jarvis.app.frontend.widgets.SpeechState
import com.jarvis.app.frontend.widgets.TapAction
import com.jarvis.app.frontend.widgets.refreshReactorWidgets
import com.jarvis.app.frontend.widgets.widgetTapAction

val Panel = Color(0xFF121B2E)
val Accent = Color(0xFFF59E0B)
val Cyan = Color(0xFF22D3EE)
val BotGray = Color(0xFF182238)
val Muted = Color(0xFF8B949E)
val Good = Color(0xFF3FB950)
val JarvisRed = Color(0xFFE5484D)

class JarvisVmFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return JarvisViewModel(app) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                JarvisScreen()
            }
        }
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Service may have died while away — re-sync the Wake toggle.
        try {
            sharedJarvisVm(application).syncWakeState()
        } catch (_: Exception) {
        }
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.action == WakeService.ACTION_WAKE_COMMAND) {
            intent.action = null // consume (avoid re-trigger on rotation)
            setIntent(intent)
            try {
                sharedJarvisVm(application)
                    .startConvoSession()
            } catch (_: Exception) {
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val autoSend = intent.getBooleanExtra(StarkShareActivity.EXTRA_AUTO_SEND, false)
            intent.action = null // consume
            setIntent(intent)
            if (shared.isNotBlank()) {
                try {
                    sharedJarvisVm(application)
                        .let { vm ->
                            if (autoSend) vm.send(shared)
                            else startActivity(
                                Intent(this, StarkShareActivity::class.java)
                                    .putExtra(Intent.EXTRA_TEXT, shared)
                            )
                        }
                } catch (_: Exception) {
                }
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val stream: android.net.Uri? = try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } catch (_: Exception) {
                null
            }
            intent.action = null // consume
            setIntent(intent)
            if (stream != null) {
                try {
                    startActivity(
                        Intent(this, StarkShareActivity::class.java)
                            .putExtra(Intent.EXTRA_STREAM, stream)
                    )
                } catch (_: Exception) {
                }
            }
        }
        if (intent?.action == "com.jarvis.app.NEW_CHAT") {
            intent.action = null // consume
            setIntent(intent)
            try {
                sharedJarvisVm(application).newChat()
            } catch (_: Exception) {
            }
        }
        if (intent?.action == "com.jarvis.app.BRIEFING") {
            intent.action = null // consume
            setIntent(intent)
            try {
                sharedJarvisVm(application).showBriefing = true
            } catch (_: Exception) {
            }
        }
        if (intent?.action == StarkWidgetProvider.ACTION_STARK_WAKE) {
            val wakeOn = intent.getBooleanExtra(StarkWidgetProvider.EXTRA_WAKE_ON, false)
            intent.action = null // consume
            setIntent(intent)
            try {
                sharedJarvisVm(application)
                    .setWakeEnabled(wakeOn)
            } catch (_: Exception) {
            }
        }
        if (intent?.action == ACTION_WIDGET_TAP) {
            StarkSounds.click()
            intent.action = null // consume
            setIntent(intent)
            try {
                val vm = sharedJarvisVm(application)
                when (widgetTapAction(SpeechState.speaking, vm.wakeOn)) {
                    TapAction.INTERRUPT -> {
                        vm.interruptSpeech()
                        if (WakeService.isRunning) {
                            startService(
                                Intent(this, WakeService::class.java).setAction(WakeService.ACTION_HUSH)
                            )
                        }
                        Toast.makeText(this, "Interrupted.", Toast.LENGTH_SHORT).show()
                    }
                    TapAction.WAKE_ON -> {
                        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        val overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
                        if (micOk && overlayOk && SpeechRecognizer.isRecognitionAvailable(this)) {
                            vm.setWakeEnabled(true)
                            Toast.makeText(this, "Wake word on — say “Hey Jarvis”.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Allow mic + display-over-apps first (tap Wake in the app).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    TapAction.WAKE_OFF -> {
                        vm.setWakeEnabled(false)
                        Toast.makeText(this, "Wake word off.", Toast.LENGTH_SHORT).show()
                    }
                }
                refreshReactorWidgets(this)
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun JarvisScreen() {
    val context = LocalContext.current
    val vm: JarvisViewModel = remember {
        sharedJarvisVm(context.applicationContext as Application)
    }

    // Permission launchers (set flags only — effects below drive the follow-ups).
    var wakeRequest by remember { mutableStateOf(false) }
    var wakeChain by remember { mutableStateOf(false) }
    var micGrantedTick by remember { mutableStateOf(0) }
    var notifTick by remember { mutableStateOf(0) }
    var phoneTick by remember { mutableStateOf(0) }
    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (wakeRequest) {
                wakeRequest = false
                micGrantedTick++
            } else {
                vm.startListening(fromUser = true)
            }
        } else {
            wakeRequest = false
            Toast.makeText(context, "Mic permission needed for voice input", Toast.LENGTH_SHORT).show()
        }
    }
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) notifTick++
        else Toast.makeText(context, "Allow notifications for the listening indicator", Toast.LENGTH_LONG).show()
    }
    val phonePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Nice-to-have (pause wake on calls): proceed even if denied.
        if (!granted) Toast.makeText(context, "Call detection off — wake pauses on calls anyway", Toast.LENGTH_LONG).show()
        phoneTick++
    }
    val devicePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Permission denied — Jarvis can't do that one.", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(vm.permRequest) {
        vm.permRequest?.let { devicePerm.launch(it); vm.permRequest = null }
    }
    LaunchedEffect(vm.captureHideTick) {
        if (vm.captureHideTick > 0) {
            try {
                (context as? android.app.Activity)?.moveTaskToBack(true)
            } catch (_: Exception) {
            }
        }
    }
    val batteryFix = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Battery unrestricted — wake stays alive", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Battery still optimized — wake may be killed", Toast.LENGTH_LONG).show()
        }
        vm.batteryStateTick++
    }
    LaunchedEffect(vm.batteryFixTick) {
        if (vm.batteryFixTick > 0) {
            val req = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.packageName)
            )
            var launched = false
            try {
                batteryFix.launch(req)
                launched = true
            } catch (_: Exception) {
            }
            if (!launched) {
                // Some OEMs strip the one-tap dialog — fall back to the list.
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        context, "Open Settings > Battery > Jarvis > Unrestricted",
                        Toast.LENGTH_LONG
                    ).show()
                }
                vm.batteryStateTick++
            }
        }
    }
    fun hasMicPerm(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    fun hasNotifPerm(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    fun hasPhonePerm(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }
    fun onMicTap() {
        if (vm.listening) {
            vm.stopListening()
            return
        }
        if (hasMicPerm()) vm.startListening(fromUser = true)
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun onWakeTap(fromChain: Boolean = false) {
        if (vm.wakeOn) {
            wakeChain = false
            vm.setWakeEnabled(false)
            return
        }
        val inChain = fromChain || wakeChain
        if (!hasMicPerm()) {
            wakeRequest = true
            wakeChain = true
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            wakeChain = true
            Toast.makeText(
                context,
                "Allow 'Display over other apps', then tap Wake again",
                Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            } catch (_: Exception) {
            }
            return
        }
        if (!hasNotifPerm()) {
            wakeChain = true
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!hasPhonePerm()) {
            wakeChain = true
            phonePerm.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }
        // All access granted. A grant chain must NOT flip wake on by itself —
        // turning it on always needs its own explicit tap.
        wakeChain = false
        if (inChain) {
            Toast.makeText(context, "Wake access granted — tap Wake to turn it on", Toast.LENGTH_LONG).show()
            return
        }
        vm.setWakeEnabled(true)
    }
    LaunchedEffect(micGrantedTick) {
        if (micGrantedTick > 0 && wakeChain) onWakeTap(fromChain = true)
    }
    LaunchedEffect(notifTick) {
        if (notifTick > 0 && wakeChain) onWakeTap(fromChain = true)
    }
    LaunchedEffect(phoneTick) {
        if (phoneTick > 0 && wakeChain) onWakeTap(fromChain = true)
    }
    LaunchedEffect(Unit) { vm.checkWhatsNew() }
    LaunchedEffect(Unit) {
        InterruptBus.requests.collect { vm.interruptSpeech() }
    }

    ThemedBackground(vm.cyberMode) {
    Column(Modifier.fillMaxSize()) {
        HudTopBar(
            online = vm.brainOk,
            wakeOn = vm.wakeOn,
            ttsOn = vm.ttsOn,
            cyber = vm.cyberMode,
            onSettings = vm::openSettings,
            onNewChat = vm::newChat,
            onChats = { vm.showChats = true },
            onMemory = { vm.showMemory = true },
            onList = { vm.showList = true },
            onToggleTts = vm::toggleTts,
            onWake = { onWakeTap() },
            onInterrupt = vm::interruptSpeech
        )
        val listState = rememberLazyListState()
        LaunchedEffect(vm.messages.size, vm.busy) {
            if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages, key = { it.time }) { Bubble(it, vm::retryLast, vm::speakText, cyber = vm.cyberMode) }
            if (vm.busy) {
                item { ThinkingRow(vm.cyberMode) }
            }
        }
        if (vm.messages.size <= 1 && !vm.busy) {
            LaunchedEffect(Unit) { vm.refreshDashboard() }
            val coreLvl by BubbleLevelBus.level.collectAsState()
            val coreHud by HudStateBus.state.collectAsState()
            if (vm.cyberMode) CyberBootBanner()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background((if (vm.cyberMode) CyberPanel else Color(0xFF0D1526)).copy(alpha = 0.85f))
                        .border(1.dp, (if (vm.cyberMode) CyberDim else HudGold).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "MATRIX // ARC CORE", color = if (vm.cyberMode) CyberGreen else HudGold, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            coreStateLabel(vm.listening, vm.busy, coreHud.speaking, vm.convoActive),
                            color = if (vm.cyberMode) CyberDim else HudCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    ArcCoreReactor(
                        listening = vm.listening,
                        thinking = vm.busy,
                        speaking = coreHud.speaking,
                        level = coreLvl,
                        onTap = vm::interruptSpeech
                    )
                    if (vm.listening || coreHud.speaking) {
                        AcousticArray(level = coreLvl, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Text(
                        hudReadoutLine(vm.dashTemp, vm.dashPing, vm.dashBatt),
                        color = if (vm.cyberMode) CyberDim else Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { vm.refreshDashboard() }.padding(4.dp)
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StarterChip("What can you do?", vm.cyberMode) { vm.send("What can you do?") }
                StarterChip("Calculate 15% of 240", vm.cyberMode) { vm.send("Calculate 15% of 240") }
                StarterChip("Motivate me", vm.cyberMode) { vm.send("Motivate me in one line") }
            }
        }
        InputRow(
            onSend = vm::send,
            onMic = ::onMicTap,
            micVisible = voiceAvailable(context),
            listening = vm.listening,
            heard = vm.lastHeard,
            heardFresh = vm.heardFresh,
            voiceNote = vm.voiceNote,
            cyber = vm.cyberMode
        )
    }
    }

    if (vm.showSettings) SettingsDialog(vm)
    if (vm.showChats) ChatsDialog(vm)
    if (vm.showMemory) MemoryDialog(vm)
    if (vm.showList) ListDialog(vm)
    if (vm.showOnboard) OnboardDialog(vm, ::onMicTap, { onWakeTap() })
    else if (vm.showWhatsNew) WhatsNewDialog(vm)
    if (vm.showBriefing) BriefingDialog(vm)
    if (vm.showHooks) HooksDialog(vm)
    if (vm.showReminders) RemindersDialog(vm)
}

private fun voiceAvailable(context: android.content.Context): Boolean {
    return try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }
}

@Composable
fun HudTopBar(
    online: Boolean,
    wakeOn: Boolean,
    ttsOn: Boolean,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    onChats: () -> Unit,
    onMemory: () -> Unit,
    onList: () -> Unit,
    onToggleTts: () -> Unit,
    onWake: () -> Unit,
    onInterrupt: () -> Unit,
    cyber: Boolean = false
) {
    val accent = if (cyber) CyberGreen else HudCyan
    val ink = if (cyber) CyberGreen else HudInk
    val busLvl by BubbleLevelBus.level.collectAsState()
    val wakePulse by animateFloatAsState(if (wakeOn) busLvl else 0f)
    val hud by HudStateBus.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(if (cyber) Color.Black else Color(0xFF0A1424))) {
        Box {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "J.A.R.V.I.S", color = ink, fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                    Text(
                        hudStatusLine(online, wakeOn),
                        color = if (online) accent else JarvisRed,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }
                HeaderMiniReactor(isSpeaking = hud.speaking, onInterrupt = onInterrupt)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = accent)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("\uFF0B New chat", color = accent) },
                    onClick = { menuOpen = false; onNewChat() }
                )
                DropdownMenuItem(
                    text = { Text("🧠 Memory", color = accent) },
                    onClick = { menuOpen = false; onMemory() }
                )
                DropdownMenuItem(
                    text = { Text("📝 Lists", color = accent) },
                    onClick = { menuOpen = false; onList() }
                )
                DropdownMenuItem(
                    text = { Text(if (ttsOn) "🔊 Voice on" else "🔇 Voice off", color = accent) },
                    onClick = { menuOpen = false; onToggleTts() }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onChats) {
                Text("\u25A4 CHATS", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = accent)
            }
            TextButton(onClick = onWake) {
                Text(
                    if (wakeOn) "\u25C9 WAKE ON" else "\u25CE WAKE",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = if (wakeOn) JarvisRed else accent,
                    modifier = Modifier.graphicsLayer { val sc = 1f + 0.18f * wakePulse; scaleX = sc; scaleY = sc }
                )
            }
        }
    }
}

/** "h:mm a" stamp for chat bubbles (blank when unknown). Pure. */
fun fmtTime(ts: Long): String {
    if (ts <= 0) return ""
    return try {
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun ThinkingRow(cyber: Boolean = false) {
    val glow by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "think"
    )
    Box(Modifier.fillMaxWidth()) {
        Text(
            if (cyber) "> working…" else "● Jarvis is thinking…",
            color = (if (cyber) CyberGreen else HudCyan).copy(alpha = 0.45f + 0.55f * glow),
            fontSize = 14.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(14.dp))
                .background(if (cyber) CyberPanel else BotGray)
                .padding(12.dp)
        )
    }
}

private fun genImageUri(context: Context, path: String): android.net.Uri? = try {
    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
} catch (_: Exception) { null }

private fun openGenImage(context: Context, path: String) {
    try {
        val uri = genImageUri(context, path) ?: return
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Couldn't open the image", Toast.LENGTH_SHORT).show()
    }
}

private fun shareGenImage(context: Context, path: String) {
    try {
        val uri = genImageUri(context, path) ?: return
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("image/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share image"
            )
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Couldn't share the image", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Bubble(m: ChatMessage, onRetry: () -> Unit, onSpeak: (String) -> Unit, modifier: Modifier = Modifier, cyber: Boolean = false) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val segs = remember(m.text) { splitCodeBlocks(m.text) }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.align(if (cyber || !isUser) Alignment.CenterStart else Alignment.CenterEnd)
                .then(if (cyber) Modifier.fillMaxWidth() else Modifier.widthIn(max = 300.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val genPath = m.imagePath
            if (!isUser && genPath != null) {
                val art = remember(genPath) {
                    try {
                        BitmapFactory.decodeFile(genPath)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (art != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Image(
                            art, "Generated image",
                            modifier = Modifier.widthIn(max = 300.dp).heightIn(max = 360.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openGenImage(context, genPath) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { shareGenImage(context, genPath) }) { Text("Share", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                        }
                    }
                }
            }
            segs.forEach { s ->
                if (!s.isCode) {
                    if (cyber) CyberMessageLine(s.text, isUser) else AnimatedGlassBubble(s.text, isUser)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!isUser) {
                            TextButton(onClick = { onSpeak(s.text) }) { Text("🔊 Speak", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(s.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                        if (!isUser && s.text.startsWith("⚠")) {
                            TextButton(onClick = onRetry) { Text("↻ Retry", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                        }
                    }
                } else {
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (cyber) CyberPanel else Color(0xFF0B1220))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TERMINAL // " + s.lang, color = if (cyber) CyberGreen else Cyan, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(s.text))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                            TextButton(onClick = {
                                try {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, codeShareText(s.lang, s.text))
                                    context.startActivity(Intent.createChooser(send, "Share code"))
                                } catch (_: Exception) {
                                }
                            }) { Text("Share", fontSize = 12.sp, color = if (cyber) CyberGreen else Color.Unspecified) }
                        }
                        SelectionContainer {
                            Text(
                                s.text,
                                color = Color(0xFFE6EDF3),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterChip(label: String, cyber: Boolean = false, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = if (cyber) AssistChipDefaults.assistChipColors(containerColor = Color.Black, labelColor = CyberGreen)
            else AssistChipDefaults.assistChipColors()
    )
}

@Composable
fun InputRow(onSend: (String) -> Unit, onMic: () -> Unit, micVisible: Boolean, listening: Boolean, heard: String, heardFresh: Boolean, voiceNote: String?, cyber: Boolean = false) {
    var input by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth()) {
        if (listening) {
            Text(
                "🎙 Listening… speak now (tap mic to stop)",
                color = JarvisRed, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        if (!listening && voiceNote != null) {
            Text(
                "⚠ " + voiceNote.orEmpty(),
                color = Color(0xFFF59E0B), fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        } else if (!listening && heardFresh && heard.isNotEmpty()) {
            Text(
                "Heard: “" + heard.take(120) + "”",
                color = if (cyber) CyberGreen else HudCyan, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        if (cyber) {
            CyberInputBar(
            text = input,
            onTextChanged = { input = it },
            onSend = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend(it)
                input = ""
            },
            isListening = listening,
            onMicTap = {
                if (micVisible) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMic()
                }
            },
            micEnabled = micVisible
            )
        } else {
            FluidInputBar(
            text = input,
            onTextChanged = { input = it },
            onSend = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend(it)
                input = ""
            },
            isListening = listening,
            onMicTap = {
                if (micVisible) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMic()
                }
            },
            micEnabled = micVisible
            )
        }
    }
}

@Composable
private fun MoreRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
    }
}

@Composable
fun SettingsDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    val acc = if (cyber) CyberGreen else Accent
    var key by remember { mutableStateOf(vm.apiKey) }
    var gh by remember { mutableStateOf(vm.githubToken) }
    val setCtx = LocalContext.current
    var masterTaps by remember { mutableStateOf(0) }
    val models = vm.availableModels.toList().ifEmpty { Models.FALLBACK }
    var model by remember { mutableStateOf(vm.model) }
    var provider by remember { mutableStateOf(vm.aiProvider) }
    var oaiKey by remember { mutableStateOf(vm.openaiKey) }
    var oaiBase by remember { mutableStateOf(vm.openaiBase) }
    var oaiModel by remember { mutableStateOf(vm.openaiModel) }
    LaunchedEffect(models.joinToString()) {
        if (model !in models && models.isNotEmpty()) model = models[0]
    }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showSettings = false },
        title = {
            Text(
                "Jarvis Settings",
                modifier = Modifier.clickable {
                    if (!vm.masterUnlocked) {
                        masterTaps++
                        if (masterTaps >= 5) {
                            vm.setMasterUnlocked()
                            Toast.makeText(setCtx, "Master section unlocked", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(setCtx, (5 - masterTaps).toString() + " taps to unlock master", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.masterUnlocked) MasterKeySection(vm, cyber)
                Text("AI provider", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .selectable(selected = provider == AI_GEMINI, onClick = { provider = AI_GEMINI })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = provider == AI_GEMINI, onClick = { provider = AI_GEMINI })
                        Text("Gemini", fontSize = 14.sp)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .selectable(selected = provider == AI_OPENAI, onClick = { provider = AI_OPENAI })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = provider == AI_OPENAI, onClick = { provider = AI_OPENAI })
                        Text("Other AI", fontSize = 14.sp)
                    }
                }
                if (provider == AI_OPENAI) {
                    Text(
                        "Any OpenAI-compatible API: OpenAI, Groq, xAI, DeepSeek, Ollama… Falls back to Gemini if it fails.",
                        fontSize = 13.sp, color = mut
                    )
                    HudTextField(cyber = cyber,
                        value = oaiKey,
                        onValueChange = { oaiKey = it.trim() },
                        placeholder = { Text("Other-AI key (sk-…)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudTextField(cyber = cyber,
                        value = oaiBase,
                        onValueChange = { oaiBase = it.trim() },
                        placeholder = { Text("Base URL — blank = OpenAI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HudTextField(cyber = cyber,
                        value = oaiModel,
                        onValueChange = { oaiModel = it.trim() },
                        placeholder = { Text("Model (gpt-4o-mini)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("API key (optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Only needed if you want to use your own key.",
                    fontSize = 13.sp, color = mut
                )
                HudTextField(cyber = cyber,
                    value = key,
                    onValueChange = { key = it.trim() },
                    placeholder = { Text("Paste your key (AIza…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("GitHub token (for repo access)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Fine-grained, read-only is enough. Stored encrypted on this phone.",
                    fontSize = 13.sp, color = mut
                )
                HudTextField(cyber = cyber,
                    value = gh,
                    onValueChange = { gh = it.trim() },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (vm.githubStatus().isNotBlank()) {
                    Text(vm.githubStatus(), fontSize = 13.sp, color = acc)
                }
                if (vm.settingsMsg.isNotBlank()) {
                    Text(vm.settingsMsg, fontSize = 13.sp, color = acc)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("💻 Cyber Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Linux terminal theme for the chat screen.",
                            fontSize = 13.sp, color = mut
                        )
                    }
                    Switch(checked = vm.cyberMode, onCheckedChange = { vm.toggleCyberMode() })
                }
                // Models stay hidden on the built-in key — only shown with your own key.
                if (provider == AI_GEMINI && key.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.refreshModels(key) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh models")
                        }
                    }
                    Text("Preferred model (auto-falls-back on quota):", fontSize = 13.sp, color = mut)
                    LazyColumn(Modifier.heightIn(max = 140.dp)) {
                        items(models) { m ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(selected = model == m, onClick = { model = m })
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = model == m, onClick = { model = m })
                                Text(m, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(key, model, provider, oaiKey, oaiBase, oaiModel); vm.saveGithubToken(gh) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { vm.showSettings = false }) { Text("Cancel") }
        }
    )
}

@Composable
fun HooksDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var post by remember { mutableStateOf(false) }
    val hooks = remember(vm.hookTick) { vm.hooks() }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showHooks = false },
        title = { Text("🔌 Smart actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Name it, paste a URL (Home Assistant, IFTTT, ESP…), then say turn on ....",
                    fontSize = 13.sp, color = mut
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Name: bedroom light") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { post = !post }) { Text(if (post) "POST" else "GET") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = url,
                        onValueChange = { url = it.trim() },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.addHook(name, url, if (post) "POST" else "GET")
                        name = ""
                        url = ""
                    }) { Text("Add") }
                }
                if (hooks.isEmpty()) {
                    Text("No actions yet.", color = mut, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(hooks) { h ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("• " + h.name, fontSize = 14.sp)
                                    Text(h.method + " " + h.url.take(48), fontSize = 11.sp, color = mut)
                                }
                                IconButton(onClick = { vm.removeHook(h.name) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = mut,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showHooks = false }) { Text("Close") }
        }
    )
}

@Composable
fun BriefingDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    val rows = remember { formatBriefing(vm.collectBriefing()) }
    val now = remember {
        java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM - h:mm a")
        )
    }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showBriefing = false },
        title = { Text("⚡ Briefing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(now, fontSize = 13.sp, color = mut)
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(k, fontSize = 14.sp, color = mut, modifier = Modifier.weight(1f))
                        Text(v, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                MoreRow("☀ Daily briefing " + if (vm.dailyBriefing) "on" else "off") { vm.toggleDailyBriefing() }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showBriefing = false }) { Text("Close") }
        }
    )
}


@Composable
fun MasterKeySection(vm: JarvisViewModel, cyber: Boolean = false) {
    val mut = if (cyber) CyberDim else Muted
    val acc = if (cyber) CyberGreen else Accent
    var mkey by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var imp by remember { mutableStateOf("") }
    Text("🔑 Master Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (!vm.masterInstalled) {
        Text(
            "Enter your master key — identity loads automatically.",
            fontSize = 13.sp, color = mut
        )
        HudTextField(cyber = cyber,
            value = mkey,
            onValueChange = { mkey = it.trim() },
            placeholder = { Text("Choose a master key (4+ chars)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.installMaster(mkey, MASTER_SELF_NAME, ""); mkey = "" }) {
            Text("Install master key")
        }
        HudTextField(cyber = cyber,
            value = imp,
            onValueChange = { imp = it.trim() },
            placeholder = { Text("...or paste a master card to import") },
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.importMasterCard(imp); imp = "" }) {
            Text("Import master card")
        }
    } else {
        Text(
            "Master mode active — recognized as " + vm.masterName.ifBlank { "Master" } + ".",
            fontSize = 13.sp, color = acc
        )
        HudTextField(cyber = cyber,
            value = confirm,
            onValueChange = { confirm = it.trim() },
            placeholder = { Text("Current key to remove") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { if (vm.removeMaster(confirm)) confirm = "" }) {
            Text("Remove master key")
        }
        Button(onClick = { vm.shareMasterCard() }) {
            Text("Share master card")
        }
    }
}

@Composable
fun ChatsDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    var q by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatData?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var armDelete by remember { mutableStateOf<String?>(null) }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showChats = false },
        title = { Text("💬 Chats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.chats.size > 1) {
                    HudTextField(cyber = cyber,
                        value = q,
                        onValueChange = { q = it },
                        placeholder = { Text("Search chats...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.exportChat() }) { Text("📤 Share open chat", fontSize = 13.sp) }
                }
                val shown = remember(q, vm.chats.size) {
                    if (q.isBlank()) vm.chats.toList()
                    else vm.chats.filter { it.title.contains(q, ignoreCase = true) }
                }
                if (shown.isEmpty()) {
                    Text(
                        if (vm.chats.isEmpty()) "No chats yet." else "No matches.",
                        color = mut, fontSize = 14.sp
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { c ->
                            val active = c.id == vm.activeChatId
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.switchChat(c.id) }
                                    .background(if (active) (if (cyber) CyberPanel else BotGray) else Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        c.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${c.msgs.count { it.r == "user" }} messages" +
                                            if (active) " • open" else "",
                                        fontSize = 12.sp, color = mut
                                    )
                                }
                                IconButton(onClick = { renameTarget = c; renameText = c.title }) {
                                    Text("✎", fontSize = 18.sp, color = mut)
                                }
                                IconButton(onClick = {
                                    if (armDelete == c.id) { vm.deleteChat(c.id); armDelete = null }
                                    else armDelete = c.id
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = if (armDelete == c.id) "Tap again to delete" else "Delete chat",
                                        tint = if (armDelete == c.id) Color.Red else mut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    if (confirmClear) { vm.clearChats(); confirmClear = false }
                    else confirmClear = true
                }) { Text(if (confirmClear) "Tap again to clear all" else "🗑 Clear all") }
                TextButton(onClick = { vm.newChat() }) { Text("＋ New chat") }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showChats = false }) { Text("Close") }
        }
    )

    if (renameTarget != null) {
        HudDialog(cyber = cyber,
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                HudTextField(cyber = cyber,
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { vm.renameChat(it.id, renameText) }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun OnboardDialog(vm: JarvisViewModel, onMic: () -> Unit, onWake: () -> Unit) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    val context = LocalContext.current
    fun hasMic(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.finishOnboard() },
        title = { Text("👋 Welcome to Jarvis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Three quick steps to wake me up:", fontSize = 14.sp)
                OnboardRow(cyber = cyber, done = hasMic(), label = "Microphone for voice input", btn = "Allow", onBtn = onMic)
                OnboardRow(cyber = cyber, done = vm.wakeOn, label = "Hey Jarvis wake word + HUD bubble", btn = "Enable", onBtn = onWake)
                OnboardRow(cyber = cyber, done = vm.batteryUnrestricted(),
                    label = "Unrestricted battery (survive reboot)",
                    btn = "Fix",
                    onBtn = vm::requestBatteryUnrestricted
                )
                OnboardRow(cyber = cyber, done = remember { isAccessEnabled(context) },
                    label = "Screen control (read, tap, scroll)",
                    btn = "Enable",
                    onBtn = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {
                        }
                    }
                )
                Text("Then just talk to me. Try a starter chip below.", fontSize = 13.sp, color = mut)
            }
        },
        confirmButton = { TextButton(onClick = { vm.finishOnboard() }) { Text("Start") } }
    )
}

@Composable
private fun OnboardRow(cyber: Boolean = false, done: Boolean, label: String, btn: String, onBtn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (done) "✓" else "○",
            color = if (done) (if (cyber) CyberGreen else Good) else (if (cyber) CyberDim else Muted),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!done) Button(onClick = onBtn) { Text(btn, fontSize = 12.sp) }
    }
}

@Composable
fun RemindersDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    val acc = if (cyber) CyberGreen else Accent
    val items = remember(vm.remTick) { vm.reminderItems() }
    val now = remember { System.currentTimeMillis() }
    var newRem by remember { mutableStateOf("") }
    var remMsg by remember { mutableStateOf("") }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showReminders = false },
        title = { Text("⏰ Reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Or say: remind me in 10 minutes to stretch.", fontSize = 13.sp, color = mut)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = newRem,
                        onValueChange = { newRem = it },
                        placeholder = { Text("in 10 minutes to stretch") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        remMsg = vm.addReminderText(newRem)
                        if (remMsg.startsWith("I\'ll remind")) newRem = ""
                    }) { Text("Add") }
                }
                if (remMsg.isNotBlank()) Text(remMsg, fontSize = 12.sp, color = acc)
                if (items.isEmpty()) {
                    Text("No reminders set.", color = mut, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(items) { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.text, fontSize = 14.sp)
                                    Text(dueText(r.at, now), fontSize = 12.sp, color = acc)
                                }
                                IconButton(onClick = { vm.deleteReminder(r.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Cancel",
                                        tint = mut,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showReminders = false }) { Text("Close") }
        }
    )
}

@Composable
fun WhatsNewDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val acc = if (cyber) CyberGreen else Accent
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showWhatsNew = false },
        title = { Text(if (vm.whatsNewFresh) "Welcome to Jarvis" else "What's new") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.whatsNewItems.forEach { e ->
                    Text("v${e.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = acc)
                    e.features.forEach { f -> Text("• $f", fontSize = 14.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showWhatsNew = false }) { Text("Let's go") }
        }
    )
}

@Composable
fun ListDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    var todoInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    val todos = remember(vm.listTick) { vm.todoItems() }
    val notes = remember(vm.listTick) { vm.noteItems() }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showList = false },
        title = { Text("📝 Lists") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Todos (or say “add … to my list”):", fontSize = 13.sp, color = mut)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = todoInput,
                        onValueChange = { todoInput = it },
                        placeholder = { Text("Add a todo…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.addTodo(todoInput); todoInput = "" }) { Text("Add") }
                }
                if (todos.isEmpty()) {
                    Text("List is empty.", color = mut, fontSize = 14.sp)
                } else {
                    todos.forEachIndexed { i, x ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = x.done, onCheckedChange = { vm.toggleTodo(i) })
                            Text(
                                x.text, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                color = if (x.done) mut else Color.Unspecified
                            )
                            IconButton(onClick = { vm.removeTodo(i) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = mut,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Text("Notes (or say “note …”):", fontSize = 13.sp, color = mut)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        placeholder = { Text("Add a note…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.addNote(noteInput); noteInput = "" }) { Text("Add") }
                }
                if (notes.isEmpty()) {
                    Text("No notes yet.", color = mut, fontSize = 14.sp)
                } else {
                    notes.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("• $n", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeNote(n) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = mut,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showList = false }) { Text("Close") }
        }
    )
}

@Composable
fun MemoryDialog(vm: JarvisViewModel) {
    val cyber = vm.cyberMode
    val mut = if (cyber) CyberDim else Muted
    var input by remember { mutableStateOf("") }
    val mems = remember(vm.memTick) { vm.memories() }
    HudDialog(cyber = cyber,
        onDismissRequest = { vm.showMemory = false },
        title = { Text("🧠 Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Things Jarvis remembers about you (also via “remember …” in chat):",
                    fontSize = 13.sp, color = mut
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(cyber = cyber,
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Add a memory…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.addMemory(input)
                        input = ""
                    }) { Text("Add") }
                }
                if (mems.isEmpty()) {
                    Text("No memories yet.", color = mut, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(mems) { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• $m", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.removeMemory(m) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Forget",
                                        tint = mut,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mems.isNotEmpty()) {
                TextButton(onClick = { vm.clearMemories() }) { Text("Clear all") }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.showMemory = false }) { Text("Close") }
        }
    )
}

package com.jarvis.app

import android.Manifest
import android.app.Application
import android.content.Intent
import com.jarvis.app.ui.StarkShareActivity
import com.jarvis.app.hardware.StarkDeviceController
import com.jarvis.app.ui.components.AcousticArray
import com.jarvis.app.ui.components.ArcCoreReactor
import com.jarvis.app.ui.components.HeaderMiniReactor
import com.jarvis.app.ui.components.HudBackdrop
import com.jarvis.app.ui.components.HudCyan
import com.jarvis.app.ui.components.HudGold
import com.jarvis.app.ui.components.HudInk
import com.jarvis.app.ui.components.coreStateLabel
import com.jarvis.app.ui.components.hudReadoutLine
import com.jarvis.app.ui.components.hudStatusLine
import com.jarvis.app.ui.components.StarkMessageCard
import com.jarvis.app.widget.StarkWidgetProvider
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

val Bg = Color(0xFF0B1220)
val Panel = Color(0xFF121B2E)
val Accent = Color(0xFFF59E0B)
val Cyan = Color(0xFF22D3EE)
val UserBlue = Color(0xFF1F6FEB)
val BotGray = Color(0xFF182238)
val Muted = Color(0xFF8B949E)
val Good = Color(0xFF3FB950)
val Warn = Color(0xFFD29922)
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
            ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java].syncWakeState()
        } catch (_: Exception) {
        }
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.action == WakeService.ACTION_WAKE_COMMAND) {
            intent.action = null // consume (avoid re-trigger on rotation)
            setIntent(intent)
            try {
                ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java]
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
                    ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java]
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
        if (intent?.action == "com.jarvis.app.NEW_CHAT") {
            intent.action = null // consume
            setIntent(intent)
            try {
                ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java].newChat()
            } catch (_: Exception) {
            }
        }
        if (intent?.action == "com.jarvis.app.BRIEFING") {
            intent.action = null // consume
            setIntent(intent)
            try {
                ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java].showBriefing = true
            } catch (_: Exception) {
            }
        }
        if (intent?.action == StarkWidgetProvider.ACTION_STARK_WAKE) {
            val wakeOn = intent.getBooleanExtra(StarkWidgetProvider.EXTRA_WAKE_ON, false)
            intent.action = null // consume
            setIntent(intent)
            try {
                ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java]
                    .setWakeEnabled(wakeOn)
            } catch (_: Exception) {
            }
        }
        if (intent?.action == ACTION_WIDGET_TAP) {
            StarkSounds.click()
            intent.action = null // consume
            setIntent(intent)
            try {
                val vm = ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java]
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
    val vm: JarvisViewModel = viewModel(factory = remember {
        JarvisVmFactory(context.applicationContext as Application)
    })

    // Permission launchers (set flags only — effects below drive the follow-ups).
    var wakeRequest by remember { mutableStateOf(false) }
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
                vm.startListening()
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
        if (hasMicPerm()) vm.startListening()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun onWakeTap() {
        if (vm.wakeOn) {
            vm.setWakeEnabled(false)
            return
        }
        if (!hasMicPerm()) {
            wakeRequest = true
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!Settings.canDrawOverlays(context)) {
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
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!hasPhonePerm()) {
            phonePerm.launch(Manifest.permission.READ_PHONE_STATE)
            return
        }
        vm.setWakeEnabled(true)
    }
    LaunchedEffect(micGrantedTick) {
        if (micGrantedTick > 0) onWakeTap()
    }
    LaunchedEffect(notifTick) {
        if (notifTick > 0) onWakeTap()
    }
    LaunchedEffect(phoneTick) {
        if (phoneTick > 0) onWakeTap()
    }
    LaunchedEffect(Unit) { vm.checkWhatsNew() }
    LaunchedEffect(Unit) {
        InterruptBus.requests.collect { vm.interruptSpeech() }
    }

    HudBackdrop {
    Column(Modifier.fillMaxSize()) {
        HudTopBar(
            online = vm.brainOk,
            wakeOn = vm.wakeOn,
            ttsOn = vm.ttsOn,
            onSettings = vm::openSettings,
            onNewChat = vm::newChat,
            onChats = { vm.showChats = true },
            onMemory = { vm.showMemory = true },
            onList = { vm.showList = true },
            onToggleTts = vm::toggleTts,
            onWake = ::onWakeTap,
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
            items(vm.messages) { Bubble(it, vm::retryLast) }
            if (vm.busy) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "Jarvis is thinking…",
                            color = Muted,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BotGray)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
        if (vm.messages.size <= 1 && !vm.busy) {
            LaunchedEffect(Unit) { vm.refreshDashboard() }
            val coreLvl by BubbleLevelBus.level.collectAsState()
            val coreHud by HudStateBus.state.collectAsState()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D1526).copy(alpha = 0.85f))
                        .border(1.dp, HudGold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "MATRIX // ARC CORE", color = HudGold, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            coreStateLabel(vm.listening, vm.busy, coreHud.speaking),
                            color = HudCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace
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
                        color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
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
                StarterChip("What can you do?") { vm.send("What can you do?") }
                StarterChip("Calculate 15% of 240") { vm.send("Calculate 15% of 240") }
                StarterChip("Motivate me") { vm.send("Motivate me in one line") }
            }
        }
        InputRow(
            onSend = vm::send,
            onMic = ::onMicTap,
            micVisible = voiceAvailable(context),
            listening = vm.listening
        )
    }
    }

    if (vm.showSettings) SettingsDialog(vm)
    if (vm.showChats) ChatsDialog(vm)
    if (vm.showMemory) MemoryDialog(vm)
    if (vm.showList) ListDialog(vm)
    if (vm.showOnboard) OnboardDialog(vm, ::onMicTap, ::onWakeTap)
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
    onInterrupt: () -> Unit
) {
    val busLvl by BubbleLevelBus.level.collectAsState()
    val wakePulse by animateFloatAsState(if (wakeOn) busLvl else 0f)
    val hud by HudStateBus.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF0A1424))) {
        Box {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = HudCyan)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "J.A.R.V.I.S", color = HudInk, fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                    Text(
                        hudStatusLine(online, wakeOn),
                        color = if (online) HudCyan else JarvisRed,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }
                HeaderMiniReactor(isSpeaking = hud.speaking, onInterrupt = onInterrupt)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = HudCyan)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("\uFF0B New chat") },
                    onClick = { menuOpen = false; onNewChat() }
                )
                DropdownMenuItem(
                    text = { Text("🧠 Memory") },
                    onClick = { menuOpen = false; onMemory() }
                )
                DropdownMenuItem(
                    text = { Text("📝 Lists") },
                    onClick = { menuOpen = false; onList() }
                )
                DropdownMenuItem(
                    text = { Text(if (ttsOn) "🔊 Voice on" else "🔇 Voice off") },
                    onClick = { menuOpen = false; onToggleTts() }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onChats) {
                Text("\u25A4 CHATS", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = HudCyan)
            }
            TextButton(onClick = onWake) {
                Text(
                    if (wakeOn) "\u25C9 WAKE ON" else "\u25CE WAKE",
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    color = if (wakeOn) JarvisRed else HudCyan,
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
fun Bubble(m: ChatMessage, onRetry: () -> Unit) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val segs = remember(m.text) { splitCodeBlocks(m.text) }
    val ts = remember(m.time) { fmtTime(m.time) }
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            segs.forEach { s ->
                if (!s.isCode) {
                    StarkMessageCard(isUser = isUser, message = s.text, timestamp = ts)
                    if (!isUser && s.text.startsWith("⚠")) {
                        TextButton(onClick = onRetry) { Text("↻ Retry", fontSize = 12.sp) }
                    }
                } else {
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0B1220))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TERMINAL // " + s.lang, color = Cyan, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(s.text))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy", fontSize = 12.sp) }
                            TextButton(onClick = {
                                try {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, codeShareText(s.lang, s.text))
                                    context.startActivity(Intent.createChooser(send, "Share code"))
                                } catch (_: Exception) {
                                }
                            }) { Text("Share", fontSize = 12.sp) }
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
private fun StarterChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
fun InputRow(onSend: (String) -> Unit, onMic: () -> Unit, micVisible: Boolean, listening: Boolean) {
    var input by remember { mutableStateOf("") }
    val busLvl by BubbleLevelBus.level.collectAsState()
    val micPulse by animateFloatAsState(if (listening) busLvl else 0f)
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (input.isBlank()) return
        onSend(input)
        input = ""
        keyboard?.hide()
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF0A1424).copy(alpha = 0.92f))) {
        if (listening) {
            Text(
                "🎙 Listening… speak now (tap mic to stop)",
                color = JarvisRed, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (micVisible) {
                IconButton(
                    onClick = onMic,
                    modifier = Modifier.graphicsLayer {
                        val s = 1f + 0.28f * micPulse; scaleX = s; scaleY = s
                    }
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (listening) "Stop listening" else "Voice input",
                        tint = if (listening) JarvisRed else HudCyan
                    )
                }
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (listening) "LISTENING…" else "ASK JARVIS…", fontFamily = FontFamily.Monospace) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0E1930),
                    unfocusedContainerColor = Color(0xFF0E1930),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = HudCyan.copy(alpha = 0.16f), contentColor = HudCyan),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
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
    var key by remember { mutableStateOf(vm.apiKey) }
    val setCtx = LocalContext.current
    var masterTaps by remember { mutableStateOf(0) }
    val models = vm.availableModels.toList().ifEmpty { Models.FALLBACK }
    var model by remember { mutableStateOf(vm.model) }
    LaunchedEffect(models.joinToString()) {
        if (model !in models && models.isNotEmpty()) model = models[0]
    }
    AlertDialog(
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
                var updatesOpen by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { updatesOpen = !updatesOpen }
                ) {
                    Text(
                        "🆕 Updates", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (updatesOpen) "▾" else "▸", fontSize = 14.sp, color = Muted)
                }
                if (updatesOpen) {
                    CHANGELOG.forEach { e ->
                        Text(
                            "v${e.name}", fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = Accent
                        )
                        e.features.forEach { f ->
                            Text("• $f", fontSize = 13.sp, color = Muted)
                        }
                    }
                }
                if (vm.masterUnlocked) MasterKeySection(vm)
                Text("API key (optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Only needed if you want to use your own key.",
                    fontSize = 13.sp, color = Muted
                )
                TextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    placeholder = { Text("Paste your key (AIza…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (vm.settingsMsg.isNotBlank()) {
                    Text(vm.settingsMsg, fontSize = 13.sp, color = Accent)
                }
                val battOk = remember(vm.batteryStateTick) { vm.batteryUnrestricted() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (battOk) "Battery: unrestricted ✓" else "Battery: optimized (wake can be killed)",
                        fontSize = 13.sp, color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    if (!battOk) TextButton(onClick = { vm.requestBatteryUnrestricted() }) { Text("Fix") }
                }
                if (autoStartTarget(Build.MANUFACTURER.orEmpty()) != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Autostart: allow Jarvis or the system kills standby",
                            fontSize = 13.sp, color = Muted,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { openAutoStartSettings(setCtx) }) { Text("Open") }
                    }
                }
                Text("More", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MoreRow("🔁 Hands-free " + if (vm.continuous) "on" else "off") { vm.toggleContinuous() }
                MoreRow("⚡ Briefing") { vm.showBriefing = true }
                MoreRow("🔌 Smart actions") { vm.showHooks = true }
                MoreRow("🎙 Mic: " + if (vm.hindiListen) "Hindi" else "Auto") { vm.toggleHindiListen() }
                MoreRow("⏰ Reminders") { vm.showReminders = true }
                MoreRow("💾 Backup") { vm.exportBackup() }
                // Models stay hidden on the built-in key — only shown with your own key.
                if (key.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.refreshModels(key) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh models")
                        }
                    }
                    Text("Preferred model (auto-falls-back on quota):", fontSize = 13.sp, color = Muted)
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
            TextButton(onClick = { vm.saveSettings(key, model) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { vm.showSettings = false }) { Text("Cancel") }
        }
    )
}

@Composable
fun HooksDialog(vm: JarvisViewModel) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var post by remember { mutableStateOf(false) }
    val hooks = remember(vm.hookTick) { vm.hooks() }
    AlertDialog(
        onDismissRequest = { vm.showHooks = false },
        title = { Text("🔌 Smart actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Name it, paste a URL (Home Assistant, IFTTT, ESP…), then say turn on ....",
                    fontSize = 13.sp, color = Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
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
                    TextField(
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
                    Text("No actions yet.", color = Muted, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(hooks) { h ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("• " + h.name, fontSize = 14.sp)
                                    Text(h.method + " " + h.url.take(48), fontSize = 11.sp, color = Muted)
                                }
                                IconButton(onClick = { vm.removeHook(h.name) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = Muted,
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
    val rows = remember { formatBriefing(vm.collectBriefing()) }
    val now = remember {
        java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM - h:mm a")
        )
    }
    AlertDialog(
        onDismissRequest = { vm.showBriefing = false },
        title = { Text("⚡ Briefing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(now, fontSize = 13.sp, color = Muted)
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(k, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
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
fun MasterKeySection(vm: JarvisViewModel) {
    var mkey by remember { mutableStateOf("") }
    var mname by remember { mutableStateOf(vm.masterName) }
    var mabout by remember { mutableStateOf(vm.masterAbout) }
    var confirm by remember { mutableStateOf("") }
    var imp by remember { mutableStateOf("") }
    Text("🔑 Master Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (!vm.masterInstalled) {
        Text(
            "Enter your master key — identity loads automatically.",
            fontSize = 13.sp, color = Muted
        )
        TextField(
            value = mkey,
            onValueChange = { mkey = it.trim() },
            placeholder = { Text("Choose a master key (4+ chars)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = mname,
            onValueChange = { mname = it },
            placeholder = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = mabout,
            onValueChange = { mabout = it },
            placeholder = { Text("About you: city, likes, work…") },
            singleLine = false,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.installMaster(mkey, mname, mabout); mkey = "" }) {
            Text("Install master key")
        }
        TextField(
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
            fontSize = 13.sp, color = Accent
        )
        TextField(
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
    var q by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatData?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var armDelete by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { vm.showChats = false },
        title = { Text("💬 Chats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.chats.size > 1) {
                    TextField(
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
                        color = Muted, fontSize = 14.sp
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { c ->
                            val active = c.id == vm.activeChatId
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.switchChat(c.id) }
                                    .background(if (active) BotGray else Color.Transparent)
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
                                        "${c.msgs.count { it.first == "user" }} messages" +
                                            if (active) " • open" else "",
                                        fontSize = 12.sp, color = Muted
                                    )
                                }
                                IconButton(onClick = { renameTarget = c; renameText = c.title }) {
                                    Text("✎", fontSize = 18.sp, color = Muted)
                                }
                                IconButton(onClick = {
                                    if (armDelete == c.id) { vm.deleteChat(c.id); armDelete = null }
                                    else armDelete = c.id
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = if (armDelete == c.id) "Tap again to delete" else "Delete chat",
                                        tint = if (armDelete == c.id) Color.Red else Muted
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
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                TextField(
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
    val context = LocalContext.current
    fun hasMic(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    AlertDialog(
        onDismissRequest = { vm.finishOnboard() },
        title = { Text("👋 Welcome to Jarvis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Three quick steps to wake me up:", fontSize = 14.sp)
                OnboardRow(done = hasMic(), label = "Microphone for voice input", btn = "Allow", onBtn = onMic)
                OnboardRow(done = vm.wakeOn, label = "Hey Jarvis wake word + HUD bubble", btn = "Enable", onBtn = onWake)
                OnboardRow(
                    done = vm.batteryUnrestricted(),
                    label = "Unrestricted battery (survive reboot)",
                    btn = "Fix",
                    onBtn = vm::requestBatteryUnrestricted
                )
                Text("Then just talk to me. Try a starter chip below.", fontSize = 13.sp, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = { vm.finishOnboard() }) { Text("Start") } }
    )
}

@Composable
private fun OnboardRow(done: Boolean, label: String, btn: String, onBtn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (done) "✓" else "○",
            color = if (done) Good else Muted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!done) Button(onClick = onBtn) { Text(btn, fontSize = 12.sp) }
    }
}

@Composable
fun RemindersDialog(vm: JarvisViewModel) {
    val items = remember(vm.remTick) { vm.reminderItems() }
    val now = remember { System.currentTimeMillis() }
    var newRem by remember { mutableStateOf("") }
    var remMsg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.showReminders = false },
        title = { Text("⏰ Reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Or say: remind me in 10 minutes to stretch.", fontSize = 13.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
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
                if (remMsg.isNotBlank()) Text(remMsg, fontSize = 12.sp, color = Accent)
                if (items.isEmpty()) {
                    Text("No reminders set.", color = Muted, fontSize = 14.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(items) { r ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.text, fontSize = 14.sp)
                                    Text(dueText(r.at, now), fontSize = 12.sp, color = Accent)
                                }
                                IconButton(onClick = { vm.deleteReminder(r.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Cancel",
                                        tint = Muted,
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
    AlertDialog(
        onDismissRequest = { vm.showWhatsNew = false },
        title = { Text(if (vm.whatsNewFresh) "Welcome to Jarvis" else "What's new") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.whatsNewItems.forEach { e ->
                    Text("v${e.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Accent)
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
    var todoInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    val todos = remember(vm.listTick) { vm.todoItems() }
    val notes = remember(vm.listTick) { vm.noteItems() }
    AlertDialog(
        onDismissRequest = { vm.showList = false },
        title = { Text("📝 Lists") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Todos (or say “add … to my list”):", fontSize = 13.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
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
                    Text("List is empty.", color = Muted, fontSize = 14.sp)
                } else {
                    todos.forEachIndexed { i, x ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = x.done, onCheckedChange = { vm.toggleTodo(i) })
                            Text(
                                x.text, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                color = if (x.done) Muted else Color.Unspecified
                            )
                            IconButton(onClick = { vm.removeTodo(i) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = Muted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Text("Notes (or say “note …”):", fontSize = 13.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
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
                    Text("No notes yet.", color = Muted, fontSize = 14.sp)
                } else {
                    notes.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("• $n", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeNote(n) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = Muted,
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
    var input by remember { mutableStateOf("") }
    val mems = remember(vm.memTick) { vm.memories() }
    AlertDialog(
        onDismissRequest = { vm.showMemory = false },
        title = { Text("🧠 Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Things Jarvis remembers about you (also via “remember …” in chat):",
                    fontSize = 13.sp, color = Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
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
                    Text("No memories yet.", color = Muted, fontSize = 14.sp)
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
                                        tint = Muted,
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

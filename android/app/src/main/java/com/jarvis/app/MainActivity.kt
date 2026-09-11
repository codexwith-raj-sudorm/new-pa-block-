package com.jarvis.app

import android.Manifest
import android.app.Application
import android.content.Intent
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
                    .startListeningDelayed(800)
            } catch (_: Exception) {
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            intent.action = null // consume
            setIntent(intent)
            if (shared.isNotBlank()) {
                try {
                    ViewModelProvider(this, JarvisVmFactory(application))[JarvisViewModel::class.java]
                        .incomingShare(shared)
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
    val devicePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Permission denied — Jarvis can't do that one.", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(vm.permRequest) {
        vm.permRequest?.let { devicePerm.launch(it); vm.permRequest = null }
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
        vm.setWakeEnabled(true)
    }
    LaunchedEffect(micGrantedTick) {
        if (micGrantedTick > 0) onWakeTap()
    }
    LaunchedEffect(notifTick) {
        if (notifTick > 0) onWakeTap()
    }
    LaunchedEffect(Unit) { vm.checkWhatsNew() }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(
            online = vm.brainOk,
            onSettings = vm::openSettings,
            onNewChat = vm::newChat,
            onChats = { vm.showChats = true },
            onMemory = { vm.showMemory = true },
            onList = { vm.showList = true },
            ttsOn = vm.ttsOn,
            onToggleTts = vm::toggleTts,
            wakeOn = vm.wakeOn,
            onWake = ::onWakeTap,
            continuous = vm.continuous,
            onToggleContinuous = vm::toggleContinuous,
            onBriefing = { vm.showBriefing = true },
            onShareChat = vm::exportChat,
            onHooks = { vm.showHooks = true },
            hindiListen = vm.hindiListen,
            onToggleHindi = vm::toggleHindiListen
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

    if (vm.showSettings) SettingsDialog(vm)
    if (vm.showChats) ChatsDialog(vm)
    if (vm.showMemory) MemoryDialog(vm)
    if (vm.showList) ListDialog(vm)
    if (vm.showWhatsNew) WhatsNewDialog(vm)
    if (vm.showShare) ShareDialog(vm)
    if (vm.showBriefing) BriefingDialog(vm)
    if (vm.showHooks) HooksDialog(vm)
}

private fun voiceAvailable(context: android.content.Context): Boolean {
    return try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }
}

@Composable
fun TopBar(
    online: Boolean,
    onSettings: () -> Unit,
    onNewChat: () -> Unit,
    onChats: () -> Unit,
    onMemory: () -> Unit,
    ttsOn: Boolean,
    onToggleTts: () -> Unit,
    wakeOn: Boolean,
    onWake: () -> Unit,
    onList: () -> Unit,
    continuous: Boolean,
    onToggleContinuous: () -> Unit,
    onBriefing: () -> Unit,
    onShareChat: () -> Unit,
    onHooks: () -> Unit,
    hindiListen: Boolean,
    onToggleHindi: () -> Unit
) {
    val busLvl by BubbleLevelBus.level.collectAsState()
    val wakePulse by animateFloatAsState(if (wakeOn) busLvl else 0f)
    Column(Modifier.fillMaxWidth().background(Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("＋ New chat") },
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
                    DropdownMenuItem(
                        text = { Text(if (continuous) "🔁 Hands-free on" else "🔁 Hands-free off") },
                        onClick = { menuOpen = false; onToggleContinuous() }
                    )
                    DropdownMenuItem(
                        text = { Text("⚡ Briefing") },
                        onClick = { menuOpen = false; onBriefing() }
                    )
                    DropdownMenuItem(
                        text = { Text("📤 Share chat") },
                        onClick = { menuOpen = false; onShareChat() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔌 Smart actions") },
                        onClick = { menuOpen = false; onHooks() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hindiListen) "🎙 Mic: Hindi" else "🎙 Mic: Auto") },
                        onClick = { menuOpen = false; onToggleHindi() }
                    )
                }
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) { Text("J", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("JARVIS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) Good else Warn))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (online) "brain connected" else "API key needed",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onChats) { Text("💬 Chats", fontSize = 13.sp) }
            TextButton(onClick = onWake) {
                Text(
                    if (wakeOn) "👂 Wake on" else "👂 Wake",
                    fontSize = 13.sp,
                    color = if (wakeOn) JarvisRed else Color.Unspecified,
                    modifier = Modifier.graphicsLayer { val s = 1f + 0.18f * wakePulse; scaleX = s; scaleY = s }
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
                    Column {
                    SelectionContainer {
                        Text(
                            s.text,
                            color = if (isUser) Color.White else Color(0xFFC9D1D9),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 14.dp, topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 14.dp
                                    )
                                )
                                .background(if (isUser) UserBlue else BotGray)
                                .padding(12.dp)
                        )
                    }
                    if (!isUser && s.text.startsWith("⚠")) {
                        TextButton(onClick = onRetry) { Text("↻ Retry", fontSize = 12.sp) }
                    }
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
                if (ts.isNotEmpty()) {
                    Text(
                        ts, color = Muted, fontSize = 11.sp,
                        modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
                    )
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
    Column(Modifier.fillMaxWidth().background(Panel)) {
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
                        tint = if (listening) JarvisRed else Accent
                    )
                }
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (listening) "Listening…" else "Ask Jarvis anything…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: JarvisViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    val models = vm.availableModels.toList().ifEmpty { Models.FALLBACK }
    var model by remember { mutableStateOf(vm.model) }
    LaunchedEffect(models.joinToString()) {
        if (model !in models && models.isNotEmpty()) model = models[0]
    }
    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        title = { Text("Jarvis Settings") },
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
                Text("🎙 Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Current: " + (
                            vm.ttsVoices.firstOrNull { it.id == vm.voiceName }?.label
                                ?: "Auto"
                            ),
                        fontSize = 13.sp, color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.previewVoice() }) { Text("Preview") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rate", fontSize = 13.sp, color = Muted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = vm.ttsRate, onValueChange = vm::setRate,
                        valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f)
                    )
                    Text("%.2f".format(vm.ttsRate), fontSize = 12.sp, color = Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pitch", fontSize = 13.sp, color = Muted, modifier = Modifier.width(44.dp))
                    Slider(
                        value = vm.ttsPitch, onValueChange = vm::setPitch,
                        valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f)
                    )
                    Text("%.2f".format(vm.ttsPitch), fontSize = 12.sp, color = Muted)
                }
                val battOk = remember { vm.batteryUnrestricted() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (battOk) "Battery: unrestricted ✓" else "Battery: optimized (wake can be killed)",
                        fontSize = 13.sp, color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    if (!battOk) TextButton(onClick = { vm.requestBatteryUnrestricted() }) { Text("Fix") }
                }
                if (vm.ttsVoices.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 210.dp)) {
                        items(vm.ttsVoices) { v ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = vm.voiceName == v.id,
                                        onClick = { vm.selectVoice(v.id) }
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = vm.voiceName == v.id,
                                    onClick = { vm.selectVoice(v.id) }
                                )
                                Text(
                                    (if (personaForKey(v.id).gender == PersonaGender.FEMALE) "♀ " else "♂ ") + v.label,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
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
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showBriefing = false }) { Text("Close") }
        }
    )
}

@Composable
fun ShareDialog(vm: JarvisViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showShare = false },
        title = { Text("⚡ Share Hub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    vm.shareText.take(220) + if (vm.shareText.length > 220) "…" else "",
                    fontSize = 13.sp, color = Muted, maxLines = 5
                )
                ShareActionRow("📝 Summarize") { vm.shareAction("sum") }
                ShareActionRow("🧒 Explain like I'm 5") { vm.shareAction("eli5") }
                ShareActionRow("🐞 Find bugs") { vm.shareAction("bugs") }
                ShareActionRow("🌐 Translate to Hindi") { vm.shareAction("tr") }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showShare = false }) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareActionRow(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
fun ChatsDialog(vm: JarvisViewModel) {
    var q by remember { mutableStateOf("") }
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
                                IconButton(onClick = { vm.deleteChat(c.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete chat",
                                        tint = Muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.newChat() }) { Text("＋ New chat") }
        },
        dismissButton = {
            TextButton(onClick = { vm.showChats = false }) { Text("Close") }
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

# JARVIS — Complete UI Code & Details (single file)

App: `com.jarvis.app` · v5.9 (versionCode 50) · minSdk 26 · Compose Material3 · snapshot 2026-09-15 · branch `arena/01a08a6b-new-pa-block` (unpushed work included).
Generated bundle — edit the real sources, not this file.

## Part 0 — UI overview

### Design system (HUD — original arc-reactor-inspired look)
- Palette: `HudCyan #67E8F9`, `HudGold #FFBA27` (focus/accents/titles), `HudInk #E6EDF3` (text), field fill `#0E1930`, dialog surface `#0B1322`, terminal card `#0B1220`, chat-code accent `Cyan #22D3EE`.
- Shared primitives (`ui/components/HudTheme.kt`): `HudTextField` (same params as Material, HUD colors), `HudDialog` (same slots as AlertDialog, HUD surface), `hudFieldColors()` (deep-navy fill, gold focus, transparent rest underline). All dialogs/fields themed.
- Reactor/voice visuals: `ArcCoreHud` (arc core + palette + status/readout lines), `WakeOrbitHud` (wake orbit animation), `AcousticArray` + `BubbleLevelBus` (live mic level), `HeaderMiniReactor`.

### Main screen (`MainActivity.kt` → `JarvisScreen`)
- `HudTopBar` (dashboard readouts: temp/ping/battery + status), chat `LazyColumn`, `InputRow` (text field + mic + live `heard`/`voiceNote`).
- Menu diet: only **New chat, Memory, Lists, Voice on/off** — everything else lives in Settings.
- `Bubble(m)` per message: prose via `StarkMessageCard` with per-segment **Speak / Copy** (+ **Retry** on `⚠` errors); fenced code blocks render as TERMINAL cards (mono, Copy/Share); generated images render as a rounded card (tap = full view, Share button).
- Dialogs: `SettingsDialog` (keys/toggles; **Master Key section hidden** — tap the settings title 5×), `HooksDialog` (smart actions), `BriefingDialog`, `ChatsDialog` (with per-chat message counts), `OnboardDialog` (mic/wake setup), `RemindersDialog`, `WhatsNewDialog` (changelog), `ListDialog`, `MemoryDialog`.
- Voice: single fixed TTS identity (Priya voice, pitch 0.68, rate 0.93); voice selection UI hidden.

### Activities / surfaces
- `MainActivity` (launcher; handles shortcut actions `com.jarvis.app.NEW_CHAT` / `BRIEFING`), `WakeHudActivity` (wake overlay), `ShotActivity` (transparent one-shot MediaProjection consent hop), `StarkShareActivity` (system share-sheet receiver → auto-send), `StarkLockActivity` (lock-screen surface).
- Home widgets: `ReactorWidget` (tap = wake on/off, tap-while-talking = interrupt), `BriefingWidget` (time/battery/next reminder), `StarkWidgetProvider` (standby/active toggle).
- `WakeTile`: Quick Settings tile for wake mode. Launcher shortcuts: New chat, Briefing.
- Notifications: screenshot/shot channel (`jarvis_shot`), capture-approval backup notification, wake alert.

## Part 1 — Kotlin UI code

### `android/app/src/main/java/com/jarvis/app/MainActivity.kt`

```kotlin
package com.jarvis.app

import android.Manifest
import android.app.Application
import android.content.Context
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
import com.jarvis.app.ui.components.HudDialog
import com.jarvis.app.ui.components.HudTextField
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
            items(vm.messages, key = { it.time }) { Bubble(it, vm::retryLast, vm::speakText) }
            if (vm.busy) {
                item { ThinkingRow() }
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
                            coreStateLabel(vm.listening, vm.busy, coreHud.speaking, vm.convoActive),
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
            listening = vm.listening,
            heard = vm.lastHeard,
            heardFresh = vm.heardFresh,
            voiceNote = vm.voiceNote
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
                    text = { Text("\uFF0B New chat", color = HudCyan) },
                    onClick = { menuOpen = false; onNewChat() }
                )
                DropdownMenuItem(
                    text = { Text("🧠 Memory", color = HudCyan) },
                    onClick = { menuOpen = false; onMemory() }
                )
                DropdownMenuItem(
                    text = { Text("📝 Lists", color = HudCyan) },
                    onClick = { menuOpen = false; onList() }
                )
                DropdownMenuItem(
                    text = { Text(if (ttsOn) "🔊 Voice on" else "🔇 Voice off", color = HudCyan) },
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
private fun ThinkingRow() {
    val glow by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "think"
    )
    Box(Modifier.fillMaxWidth()) {
        Text(
            "● Jarvis is thinking…",
            color = HudCyan.copy(alpha = 0.45f + 0.55f * glow),
            fontSize = 14.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(14.dp))
                .background(BotGray)
                .padding(12.dp)
        )
    }
}

@Composable
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

fun Bubble(m: ChatMessage, onRetry: () -> Unit, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val segs = remember(m.text) { splitCodeBlocks(m.text) }
    val ts = remember(m.time) { fmtTime(m.time) }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 300.dp),
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
                            TextButton(onClick = { shareGenImage(context, genPath) }) { Text("Share", fontSize = 12.sp) }
                        }
                    }
                }
            }
            segs.forEach { s ->
                if (!s.isCode) {
                    StarkMessageCard(isUser = isUser, message = s.text, timestamp = ts)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!isUser) {
                            TextButton(onClick = { onSpeak(s.text) }) { Text("🔊 Speak", fontSize = 12.sp) }
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(s.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy", fontSize = 12.sp) }
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
fun InputRow(onSend: (String) -> Unit, onMic: () -> Unit, micVisible: Boolean, listening: Boolean, heard: String, heardFresh: Boolean, voiceNote: String?) {
    var input by remember { mutableStateOf("") }
    val busLvl by BubbleLevelBus.level.collectAsState()
    val micPulse by animateFloatAsState(if (listening) busLvl else 0f)
    val haptic = LocalHapticFeedback.current
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
        if (!listening && voiceNote != null) {
            Text(
                "⚠ " + voiceNote.orEmpty(),
                color = Color(0xFFF59E0B), fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        } else if (!listening && heardFresh && heard.isNotEmpty()) {
            Text(
                "Heard: “" + heard.take(120) + "”",
                color = HudCyan, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (micVisible) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMic()
                    },
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
            HudTextField(
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    submit()
                },
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
    var gh by remember { mutableStateOf(vm.githubToken) }
    val setCtx = LocalContext.current
    var masterTaps by remember { mutableStateOf(0) }
    val models = vm.availableModels.toList().ifEmpty { Models.FALLBACK }
    var model by remember { mutableStateOf(vm.model) }
    LaunchedEffect(models.joinToString()) {
        if (model !in models && models.isNotEmpty()) model = models[0]
    }
    HudDialog(
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
                if (vm.masterUnlocked) MasterKeySection(vm)
                Text("API key (optional)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Only needed if you want to use your own key.",
                    fontSize = 13.sp, color = Muted
                )
                HudTextField(
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
                    fontSize = 13.sp, color = Muted
                )
                HudTextField(
                    value = gh,
                    onValueChange = { gh = it.trim() },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (vm.githubStatus().isNotBlank()) {
                    Text(vm.githubStatus(), fontSize = 13.sp, color = Accent)
                }
                if (vm.settingsMsg.isNotBlank()) {
                    Text(vm.settingsMsg, fontSize = 13.sp, color = Accent)
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
            TextButton(onClick = { vm.saveSettings(key, model); vm.saveGithubToken(gh) }) { Text("Save") }
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
    HudDialog(
        onDismissRequest = { vm.showHooks = false },
        title = { Text("🔌 Smart actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Name it, paste a URL (Home Assistant, IFTTT, ESP…), then say turn on ....",
                    fontSize = 13.sp, color = Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(
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
                    HudTextField(
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
    HudDialog(
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
    var confirm by remember { mutableStateOf("") }
    var imp by remember { mutableStateOf("") }
    Text("🔑 Master Key", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    if (!vm.masterInstalled) {
        Text(
            "Enter your master key — identity loads automatically.",
            fontSize = 13.sp, color = Muted
        )
        HudTextField(
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
        HudTextField(
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
        HudTextField(
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
    HudDialog(
        onDismissRequest = { vm.showChats = false },
        title = { Text("💬 Chats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.chats.size > 1) {
                    HudTextField(
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
                                        "${c.msgs.count { it.r == "user" }} messages" +
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
        HudDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                HudTextField(
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
    HudDialog(
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
                OnboardRow(
                    done = remember { isAccessEnabled(context) },
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
    HudDialog(
        onDismissRequest = { vm.showReminders = false },
        title = { Text("⏰ Reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Or say: remind me in 10 minutes to stretch.", fontSize = 13.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(
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
    HudDialog(
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
    HudDialog(
        onDismissRequest = { vm.showList = false },
        title = { Text("📝 Lists") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Todos (or say “add … to my list”):", fontSize = 13.sp, color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(
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
                    HudTextField(
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
    HudDialog(
        onDismissRequest = { vm.showMemory = false },
        title = { Text("🧠 Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Things Jarvis remembers about you (also via “remember …” in chat):",
                    fontSize = 13.sp, color = Muted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTextField(
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
```

### `android/app/src/main/java/com/jarvis/app/ui/WakeHudActivity.kt`

```kotlin
package com.jarvis.app.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jarvis.app.HudStateBus
import com.jarvis.app.MainActivity
import com.jarvis.app.sharedJarvisVm
import com.jarvis.app.ui.components.WakeOrbitHud
import kotlinx.coroutines.delay

/** Full-screen wake-mode interface: orbiting HUD over the shared ViewModel. */
class WakeHudActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = sharedJarvisVm(application as Application)
        setContent {
            val tick by HudStateBus.ticker.collectAsState()
            LaunchedEffect(tick) {
                if (tick?.text == "[CONVO: END]") {
                    delay(1500)
                    finish()
                }
            }
            LaunchedEffect(Unit) {
                vm.refreshDashboard()
                vm.startConvoSession()
            }
            WakeOrbitHud(
                vm,
                onMic = {
                    if (vm.listening) vm.stopListening()
                    else {
                        try {
                            vm.startListening(fromUser = true)
                        } catch (_: Exception) {
                        }
                    }
                },
                onExpand = {
                    try {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    } catch (_: Exception) {
                    }
                    finish()
                },
                onClose = { finish() }
            )
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/ShotActivity.kt`

```kotlin
package com.jarvis.app.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jarvis.app.ScreenConsent
import com.jarvis.app.ScreenshotService

/** Transparent consent hop: asks once, caches the grant, hands off, finishes. */
class ShotActivity : ComponentActivity() {
    private fun receiver(): ResultReceiver? = try {
        if (Build.VERSION.SDK_INT >= 33) getIntent().getParcelableExtra("receiver", ResultReceiver::class.java)
        else {
            @Suppress("DEPRECATION") getIntent().getParcelableExtra("receiver")
        }
    } catch (_: Exception) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(7702)
        } catch (_: Exception) {
        }
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), 7717)
        } catch (_: Exception) {
            receiver()?.send(1, Bundle().apply { putString("error", "declined") })
            Toast.makeText(this, "Screen capture isn't available here", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("legacy result path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7717 && resultCode == Activity.RESULT_OK && data != null) {
            ScreenConsent.code = resultCode
            ScreenConsent.data = data
            try {
                startForegroundService(
                    Intent(this, ScreenshotService::class.java)
                        .putExtra("code", resultCode).putExtra("data", data)
                        .putExtra("mode", getIntent().getStringExtra("mode") ?: "share")
                        .putExtra("receiver", receiver())
                )
            } catch (e: Exception) {
                receiver()?.send(1, Bundle().apply { putString("error", e.message ?: "couldn't start capture") })
                Toast.makeText(this, "Couldn't start capture", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 7717) {
            receiver()?.send(1, Bundle().apply { putString("error", "declined") })
            Toast.makeText(this, "Screen capture declined", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/StarkShareActivity.kt`

```kotlin
package com.jarvis.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.MainActivity

/** Shared-text preview for the Stark Hub (blank/null -> fallback). Pure, tested. */
fun starkSharedPreview(text: String?): String =
    text?.take(4000).orEmpty().ifBlank { "No text payload detected." }

class StarkShareActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTO_SEND = "com.jarvis.app.AUTO_SEND"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = starkSharedPreview(intent.getStringExtra(Intent.EXTRA_TEXT))

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1220)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STARK HUB // SHARE INTERCEPT",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = sharedText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { injectToJarvis(sharedText); finish() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) {
                        Text("PROCESS & INJECT TO JARVIS", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    private fun injectToJarvis(text: String) {
        try {
            val i = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(EXTRA_AUTO_SEND, true)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/StarkLockActivity.kt`

```kotlin
package com.jarvis.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class StarkLockActivity : FragmentActivity() {
    private var lockMsg by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1220)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STARK INDUSTRIES // SECURITY GATE",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ACCESS RESTRICTED // RETINAL SCAN REQUIRED",
                        color = Color(0xFFFEF3C7),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { authenticateUser() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                    ) {
                        Text("INITIATE BIOMETRIC SCAN", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    if (lockMsg.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(lockMsg, color = Color(0xFFFCA5A5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        authenticateUser()
    }

    private fun authenticateUser() {
        if (BiometricManager.from(this).canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS) {
            lockMsg = "Biometrics unavailable on this device"
            return
        }
        lockMsg = ""
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                finish() // Unlock app and return to main HUD
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                lockMsg = errString.toString()
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                lockMsg = "Not recognized — try again"
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Stark Neural Identity Check")
            .setSubtitle("Confirm identity to access classified JARVIS vault")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/components/ArcCoreHud.kt`

```kotlin
package com.jarvis.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Arc-core HUD palette. */
val HudCyan = Color(0xFF67E8F9)
val HudBlue = Color(0xFF38BDF8)
val HudAmber = Color(0xFFFBBF24)
val HudGold = Color(0xFFFFBA27)
val HudInk = Color(0xFFE6EDF3)

/** One-line status for the HUD bar. Pure, tested. */
fun hudStatusLine(online: Boolean, wakeOn: Boolean): String {
    val link = if (online) "NEURAL LINK ACTIVE" else "OFFLINE"
    return if (wakeOn) "$link \u2022 WAKE ARMED" else link
}

/** Core state label. Priority: speaking > listening > thinking > convo > standby. Pure, tested. */
fun coreStateLabel(listening: Boolean, thinking: Boolean, speaking: Boolean, convo: Boolean = false): String = when {
    speaking -> "SPEAKING"
    listening -> "LISTENING"
    thinking -> "THINKING"
    convo -> "CONVO LIVE"
    else -> "STANDBY"
}

/** Full revolution time (ms) for the reactor rings. Pure, tested. */
fun arcSpinMs(thinking: Boolean, listening: Boolean): Int = when {
    thinking -> 1200
    listening -> 2600
    else -> 9000
}

/** Readout line for the HUD hero. Pure, tested. */
fun hudReadoutLine(temp: String, ping: String, batt: Int): String {
    val t = temp.ifBlank() { "\u2014" }
    val p = ping.ifBlank() { "\u2014" }
    val b = if (batt < 0) "\u2014" else "$batt%"
    return "SYS $t \u2022 NET $p \u2022 PWR $b"
}

/** Deterministic per-bar height from mic level (0.08..1). Pure, tested. */
fun acousticBarHeight(level: Float, index: Int): Float {
    val l = level.coerceIn(0f, 1f)
    val frac = (sin(index * 12.9898f) * 43758.5453f).let { it - kotlin.math.floor(it) }
    return (0.08f + 0.92f * l * (0.35f + 0.65f * frac)).coerceIn(0.08f, 1f)
}

/** Every 5th bar is cyan (reference contrast rhythm). Pure, tested. */
fun acousticBarCyan(index: Int): Boolean = index % 5 == 0

/**
 * Reactive arc-core: tick ring, counter-rotating coil arcs, glowing core that
 * breathes with the mic/speech level, radar sweep while listening. Tap = interrupt.
 */
@Composable
fun ArcCoreReactor(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    level: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 200.dp
) {
    val spinMs = arcSpinMs(thinking, listening)
    val stateColor = when {
        speaking -> HudGold
        listening -> HudCyan
        thinking -> HudBlue
        else -> HudCyan.copy(alpha = 0.7f)
    }
    val spin by key(spinMs) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(spinMs, easing = LinearEasing)),
            label = "spin"
        )
    }
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val active = if (speaking || listening) 1f else 0.35f
    Canvas(modifier.size(sizeDp).clickable(onClick = onTap)) {
        val r = size.minDimension / 2f
        val c = center
        val lvl = level.coerceIn(0f, 1f)
        // Outer tick ring.
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val long = i % 5 == 0
            val r1 = r * (if (long) 0.90f else 0.94f)
            val r2 = r * 0.98f
            drawLine(
                stateColor.copy(alpha = if (long) 0.8f else 0.3f),
                Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                strokeWidth = if (long) 3f else 2f
            )
        }
        // Counter-rotating dashed coil arcs.
        val rr = r * 0.78f
        val arcTopLeft = Offset(c.x - rr, c.y - rr)
        val arcSize = Size(rr * 2, rr * 2)
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
        drawArc(
            stateColor.copy(alpha = 0.55f), spin, 270f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 5f, pathEffect = dash)
        )
        drawArc(
            stateColor.copy(alpha = 0.35f), -spin, 200f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 3f, pathEffect = dash)
        )
        // Coil nodes riding the slow ring.
        for (i in 0 until 10) {
            val a = Math.toRadians((i * 36f + spin / 3f).toDouble())
            val cr = r * 0.64f
            drawCircle(
                HudGold.copy(alpha = 0.9f),
                radius = r * 0.042f,
                center = Offset(c.x + cr * cos(a).toFloat(), c.y + cr * sin(a).toFloat())
            )
        }
        // Orbiting synaptic spark.
        val sa = Math.toRadians((spin * 2.5).toDouble())
        val sc = Offset(c.x + rr * cos(sa).toFloat(), c.y + rr * sin(sa).toFloat())
        drawCircle(HudGold.copy(alpha = 0.25f), radius = r * 0.06f, center = sc)
        drawCircle(Color.White, radius = r * 0.022f, center = sc)
        // Breathing core.
        val coreR = r * 0.42f * (1f + 0.05f * lvl + 0.04f * pulse * active)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, HudGold, Color.Transparent),
                center = c, radius = coreR * 1.7f
            ),
            radius = coreR * 1.7f, center = c
        )
        drawCircle(Color.White, radius = coreR * 0.32f, center = c)
        // Radar sweep while listening.
        if (listening) {
            drawArc(
                Color.White.copy(alpha = 0.85f), spin, 40f, false,
                topLeft = arcTopLeft, size = arcSize,
                style = Stroke(width = 6f)
            )
        }
    }
}

/** Dark HUD backdrop: gradient + faint grid + corner brackets. */
@Composable
fun HudBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05090F))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1626), Color(0xFF05090F), Color(0xFF0A0F1A))
                )
            )
            val step = 48.dp.toPx()
            val grid = HudBlue.copy(alpha = 0.05f)
            var x = 0f
            while (x < size.width) {
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            val len = 30f
            val m = 12f
            val bc = HudCyan.copy(alpha = 0.4f)
            val w = 3f
            val sw = size.width
            val sh = size.height
            drawLine(bc, Offset(m, m + len), Offset(m, m), w)
            drawLine(bc, Offset(m, m), Offset(m + len, m), w)
            drawLine(bc, Offset(sw - m, m + len), Offset(sw - m, m), w)
            drawLine(bc, Offset(sw - m, m), Offset(sw - m - len, m), w)
            drawLine(bc, Offset(m, sh - m - len), Offset(m, sh - m), w)
            drawLine(bc, Offset(m, sh - m), Offset(m + len, sh - m), w)
            drawLine(bc, Offset(sw - m, sh - m - len), Offset(sw - m, sh - m), w)
            drawLine(bc, Offset(sw - m, sh - m), Offset(sw - m - len, sh - m), w)
        }
        content()
    }
}

/** Gold waveform strip (every 5th bar cyan), driven by the live mic level. */
@Composable
fun AcousticArray(level: Float, modifier: Modifier = Modifier) {
    val barCount = 24
    Canvas(modifier.height(52.dp).fillMaxWidth()) {
        val gap = 4f
        val bw = (size.width - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val h = size.height * acousticBarHeight(level, i)
            drawRect(
                if (acousticBarCyan(i)) HudCyan else HudGold,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h)
            )
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/components/HeaderMiniReactor.kt`

```kotlin
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeaderMiniReactor(
    isSpeaking: Boolean,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shown iff HudStateBus.state.speaking == true; no layout shifts
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
            // 40dp arc core; tap cuts off speech immediately
            ArcCoreReactor(
                listening = false,
                thinking = false,
                speaking = true,
                level = 0.6f,
                onTap = onInterrupt,
                sizeDp = 40.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "TAP TO STOP",
                color = HudAmber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/components/HudTheme.kt`

```kotlin
package com.jarvis.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Shared HUD field palette: deep-navy fill, gold focus, transparent rest underline. */
@Composable
fun hudFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF0E1930),
    unfocusedContainerColor = Color(0xFF0E1930),
    disabledContainerColor = Color(0xFF0E1930),
    focusedIndicatorColor = HudGold,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = HudInk,
    unfocusedTextColor = HudInk,
    cursorColor = HudGold,
    focusedPlaceholderColor = Color(0xFF8B949E),
    unfocusedPlaceholderColor = Color(0xFF8B949E)
)

/** App-wide text field: same params as Material's, HUD colors by default. */
@Composable
fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: TextFieldColors = hudFieldColors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = singleLine,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors = colors
    )
}

/** App-wide dialog: same slots as Material's, HUD surface by default. */
@Composable
fun HudDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF0B1322),
        titleContentColor = HudGold,
        textContentColor = HudInk,
        tonalElevation = 0.dp
    )
}
```

### `android/app/src/main/java/com/jarvis/app/ui/components/StarkMessageCard.kt`

```kotlin
package com.jarvis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StarkMessageCard(
    isUser: Boolean,
    message: String,
    timestamp: String
) {
    val borderColor = if (isUser) Color(0xFFF59E0B).copy(alpha = 0.6f) else Color(0xFFD97706).copy(alpha = 0.3f)
    val bgColor = if (isUser) Color(0xFF1E1B4B).copy(alpha = 0.5f) else Color(0xFF0F172A).copy(alpha = 0.85f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bgColor, shape = RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, shape = RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = if (isUser) "COMMAND // USER" else "JARVIS // NEURAL_RESP",
                    color = Color(0xFFFBBF24),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = timestamp,
                    color = Color(0xFF64748B),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/ui/components/WakeOrbitHud.kt`

```kotlin
package com.jarvis.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.BubbleLevelBus
import com.jarvis.app.HudStateBus
import com.jarvis.app.JarvisViewModel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Golden-spiral node layout in the unit circle. Pure, tested. */
fun brainNode(index: Int, count: Int): Pair<Float, Float> {
    if (count <= 1) return 0f to 0f
    val r = kotlin.math.sqrt(index.toFloat() / (count - 1)) * 0.92f
    val a = index * 2.39996f
    return (r * cos(a)) to (r * sin(a))
}

/** Ring + cross-brace edges (index pairs, never self-loops). Pure, tested. */
fun brainEdges(count: Int): List<Pair<Int, Int>> {
    if (count < 3) return emptyList()
    val edges = mutableListOf<Pair<Int, Int>>()
    for (i in 0 until count) edges.add(i to (i + 1) % count)
    for (i in 0 until count / 2) edges.add(i to ((i + count / 2) % count))
    return edges
}

/** Orbit offset for an angle (deg) on an rx/ry ellipse. Pure, tested. */
fun orbitXY(angleDeg: Float, rx: Float, ry: Float): Pair<Float, Float> {
    val a = Math.toRadians(angleDeg.toDouble())
    return (cos(a).toFloat() * rx) to (sin(a).toFloat() * ry)
}

/** Front-ness 0..1 (drives the scale/alpha depth cue). Pure, tested. */
fun orbitDepth(angleDeg: Float): Float =
    (sin(Math.toRadians(angleDeg.toDouble())).toFloat() + 1f) / 2f

/** Panel scale from depth, boosted when focused. Pure, tested. */
fun focusScale(depth: Float, focused: Boolean): Float =
    (0.82f + 0.18f * depth) * (if (focused) 1.22f else 1f)

/**
 * Neural-network brain: golden nodes on cyan threads, signals riding the
 * edges, hot nodes breathing with the live voice level. Tap = interrupt.
 */
@Composable
fun BrainNetwork(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    level: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 170.dp,
    nodeCount: Int = 14
) {
    val flow by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "flow"
    )
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600), repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val threadColor = when {
        speaking -> HudGold
        listening -> HudCyan
        thinking -> HudBlue
        else -> HudCyan.copy(alpha = 0.55f)
    }
    Canvas(modifier.size(sizeDp).clickable(onClick = onTap)) {
        val r = size.minDimension / 2f
        val c = center
        val lvl = level.coerceIn(0f, 1f)
        val pts = List(nodeCount) { i ->
            val (nx, ny) = brainNode(i, nodeCount)
            Offset(c.x + nx * r, c.y + ny * r)
        }
        drawCircle(
            Brush.radialGradient(
                listOf(threadColor.copy(alpha = 0.22f), Color.Transparent),
                center = c, radius = r
            ),
            radius = r, center = c
        )
        val edges = brainEdges(nodeCount)
        for ((a, b) in edges) {
            drawLine(threadColor.copy(alpha = 0.5f), pts[a], pts[b], strokeWidth = 2f)
        }
        for (k in 0 until 3) {
            val f = (flow + k / 3f) % 1f
            val seg = (f * edges.size).toInt().coerceIn(0, edges.size - 1)
            val frac = (f * edges.size) % 1f
            val (a, b) = edges[seg]
            val p = Offset(
                pts[a].x + (pts[b].x - pts[a].x) * frac,
                pts[a].y + (pts[b].y - pts[a].y) * frac
            )
            drawCircle(HudGold.copy(alpha = 0.45f), radius = r * 0.055f, center = p)
            drawCircle(Color.White, radius = r * 0.024f, center = p)
        }
        for (i in pts.indices) {
            val hot = i % 3 == 0
            val grow = 1f + 0.25f * pulse * (if (hot) 1f else 0.4f) +
                0.35f * lvl * (if (hot) 1f else 0f)
            val nr = r * (if (hot) 0.045f else 0.028f) * grow
            drawCircle(HudGold.copy(alpha = 0.3f), nr * 2.2f, pts[i])
            drawCircle(if (hot) HudGold else threadColor, nr, pts[i])
        }
    }
}

private data class OrbitPanel(val title: String, val lines: List<String>, val gold: Boolean)

private data class Placed(val p: OrbitPanel, val pos: Offset, val depth: Float, val i: Int)

@Composable
private fun PanelCard(p: OrbitPanel, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val accent = if (p.gold) HudGold else HudCyan
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1526).copy(alpha = 0.92f))
            .border(
                if (expanded) 2.dp else 1.dp,
                accent.copy(alpha = if (expanded) 0.8f else 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            p.title, color = accent, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        p.lines.forEach { ln ->
            Text(
                ln.ifBlank { "—" }, color = HudInk, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = if (expanded) 6 else 2,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Wake-mode interface: a neural brain core with live panels (voice, heard,
 * reply, system, net) orbiting it. Tap a panel to focus it; fixed controls
 * live at the bottom.
 */
@Composable
fun WakeOrbitHud(
    vm: JarvisViewModel,
    onMic: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val level by BubbleLevelBus.level.collectAsState()
    val hud by HudStateBus.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var focused by remember { mutableStateOf<Int?>(null) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterA by animateFloatAsState(if (entered) 1f else 0f, tween(600), label = "enter")
    val now = remember {
        java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        )
    }
    HudBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val cx = wPx / 2f
            val cy = hPx * 0.40f
            val panelW = with(density) { 144.dp.toPx() }
            val panelH = with(density) { 90.dp.toPx() }
            val rx = ((wPx - panelW) / 2f - with(density) { 8.dp.toPx() }).coerceAtLeast(10f)
            val ry = ((hPx * 0.60f - panelH) / 2f).coerceAtLeast(10f)
            val orbit by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
                label = "orbit"
            )
            val bob by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200), repeatMode = RepeatMode.Reverse),
                label = "bob"
            )
            Text(
                "WAKE MODE", color = HudGold, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
            )
            val brainTopDp = with(density) { cy.toDp() } - 85.dp
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                BrainNetwork(
                    listening = vm.listening,
                    thinking = vm.busy,
                    speaking = hud.speaking,
                    level = level,
                    onTap = vm::interruptSpeech,
                    modifier = Modifier.padding(top = brainTopDp)
                )
            }
            val lastReply = vm.messages.lastOrNull { it.role != "user" }?.text.orEmpty()
            val batt = if (vm.dashBatt < 0) "—" else "${vm.dashBatt}%"
            val panels = listOf(
                OrbitPanel(
                    "VOICE",
                    listOf(coreStateLabel(vm.listening, vm.busy, hud.speaking, vm.convoActive), "LVL ${(level * 100).toInt()}%"),
                    gold = false
                ),
                OrbitPanel(
                    "HEARD",
                    listOfNotNull(vm.voiceNote, vm.lastHeard.take(120).ifEmpty { null }).ifEmpty { listOf("—") },
                    gold = false
                ),
                OrbitPanel("REPLY", listOf(lastReply.take(160)), gold = true),
                OrbitPanel("SYS", listOf(now, "PWR $batt"), gold = true),
                OrbitPanel("NET", listOf("PING " + vm.dashPing, vm.model.take(20)), gold = false)
            )
            val placed = panels.mapIndexed { i, p ->
                val a = orbit + i * 72f
                val (ox, oy) = orbitXY(a, rx, ry)
                val bobY = sin(bob * 6.283f + i * 1.256f) * 6f
                Placed(p, Offset(cx + ox - panelW / 2f, cy + oy - panelH / 2f + bobY), orbitDepth(a), i)
            }.sortedBy { it.depth }
            for (pl in placed) {
                val isF = focused == pl.i
                val sc = focusScale(pl.depth, isF)
                PanelCard(
                    pl.p,
                    Modifier.offset { IntOffset(pl.pos.x.roundToInt(), pl.pos.y.roundToInt()) }
                        .width(144.dp)
                        .graphicsLayer {
                            scaleX = sc
                            scaleY = sc
                            alpha = (0.72f + 0.28f * pl.depth) * enterA
                        }
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            focused = if (isF) null else pl.i
                        },
                    expanded = isF
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Listening — ends after 10s of quiet",
                    color = Color(0xFF8B949E), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMic) {
                        Icon(
                            if (vm.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mic",
                            tint = if (vm.listening) Color(0xFFE5484D) else HudCyan
                        )
                    }
                    IconButton(onClick = onExpand) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open app", tint = HudCyan)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = HudCyan)
                    }
                }
            }
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/BriefingWidget.kt`

```kotlin
package com.jarvis.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews

/** Home-screen briefing widget: time, battery, next reminder. Tap opens Jarvis. */
class BriefingWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.jarvis.app.BRIEFING_REFRESH"
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val comp = android.content.ComponentName(context, BriefingWidget::class.java)
            for (id in mgr.getAppWidgetIds(comp)) updateOne(context, mgr, id)
        }
    }
}

private fun updateOne(ctx: Context, mgr: AppWidgetManager, id: Int) {
    val v = RemoteViews(ctx.packageName, R.layout.widget_briefing)
    val now = java.time.LocalDateTime.now()
    v.setTextViewText(R.id.bw_time, now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
    v.setTextViewText(R.id.bw_date, now.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")) + widgetMasterLine(ctx))
    v.setTextViewText(R.id.bw_batt, "Battery " + battPct(ctx) + "%  ↻")
    val at = System.currentTimeMillis()
    val next = Store(ctx).loadReminders().filter { it.at > at }.minByOrNull { it.at }
    v.setTextViewText(R.id.bw_next, widgetReminderLine(next, at))
    val open = PendingIntent.getActivity(
        ctx, 8001, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    v.setOnClickPendingIntent(R.id.bw_root, open)
    val refresh = PendingIntent.getBroadcast(
        ctx, 8002, Intent(ctx, BriefingWidget::class.java).setAction(BriefingWidget.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    v.setOnClickPendingIntent(R.id.bw_batt, refresh)
    runCatching { mgr.updateAppWidget(id, v) }
}

/** " · Master <first>" suffix for the widget when a master key is installed. */
private fun widgetMasterLine(ctx: Context): String {
    return try {
        val s = Store(ctx)
        if (s.masterKey.isNotBlank() && s.masterName.isNotBlank()) " · Master " + firstName(s.masterName) else ""
    } catch (_: Exception) {
        ""
    }
}

private fun battPct(ctx: Context): Int {
    return try {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (l >= 0 && s > 0) l * 100 / s else -1
    } catch (_: Exception) {
        -1
    }
}

/** Widget next-reminder line. Pure, tested. */
fun widgetReminderLine(next: ReminderItem?, now: Long): String {
    if (next == null) return "No reminders"
    return "Next: " + next.text.take(30) + " " + dueText(next.at, now)
}
```

### `android/app/src/main/java/com/jarvis/app/ReactorWidget.kt`

```kotlin
package com.jarvis.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Tap action the widget's PendingIntent fires at MainActivity. */
const val ACTION_WIDGET_TAP = "com.jarvis.app.WIDGET_TAP"

/** Process-wide speech flag: VM replies and the service "Yes sir?" both report here. */
object SpeechState {
    @Volatile var speaking: Boolean = false
}

enum class TapAction { INTERRUPT, WAKE_ON, WAKE_OFF }

/** Pure tap decision (unit-tested): interrupting speech always wins over the wake toggle. */
fun widgetTapAction(speaking: Boolean, wakeOn: Boolean): TapAction =
    if (speaking) TapAction.INTERRUPT else if (wakeOn) TapAction.WAKE_OFF else TapAction.WAKE_ON

/** Mini arc-reactor home-screen widget: tap to arm wake mode, tap while talking to interrupt. */
class ReactorWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        refreshReactorWidgets(context)
    }
}

/** Rebinds every reactor widget (tap target + bright/dim state). Safe to call anywhere. */
fun refreshReactorWidgets(context: Context) {
    try {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ReactorWidget::class.java))
        if (ids.isEmpty()) return
        val tap = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).setAction(ACTION_WIDGET_TAP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_reactor)
            views.setOnClickPendingIntent(R.id.reactor_tap, tap)
            views.setInt(R.id.reactor_tap, "setAlpha", if (WakeService.isRunning) 255 else 110)
            mgr.updateAppWidget(id, views)
        }
    } catch (_: Exception) { }
}
```

### `android/app/src/main/java/com/jarvis/app/widget/StarkWidgetProvider.kt`

```kotlin
package com.jarvis.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.Store
import com.jarvis.app.WakeService

/** Widget status label for the Stark toggle (pure, tested). */
fun starkWidgetLabel(awake: Boolean): String = if (awake) "JARVIS: ACTIVE" else "STANDBY"

class StarkWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_WAKE = "com.jarvis.app.STARK_TOGGLE_WAKE"
        const val ACTION_STARK_WAKE = "com.jarvis.app.STARK_WAKE_SET"
        const val EXTRA_WAKE_ON = "com.jarvis.app.EXTRA_WAKE_ON"
        /** Live truth: the wake service is up, or wake mode is armed. */
        fun isAwake(ctx: Context): Boolean = try {
            WakeService.isRunning || Store(ctx.applicationContext).wakeEnabled
        } catch (_: Exception) {
            false
        }

        /** Rebind every Stark widget from live truth. Safe to call anywhere. */
        fun refreshAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, StarkWidgetProvider::class.java))
                if (ids.isEmpty()) return
                for (id in ids) StarkWidgetProvider().updateAppWidget(ctx, mgr, id)
            } catch (_: Exception) {
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.stark_widget_layout)

        views.setTextViewText(R.id.widget_status_text, starkWidgetLabel(isAwake(context)))

        val intent = Intent(context, StarkWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_WAKE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_reactor_icon, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WAKE) {
            val nowAwake = !isAwake(context)

            // Drive real wake-word listening via MainActivity (foreground-safe).
            try {
                val go = Intent(context, MainActivity::class.java)
                    .setAction(ACTION_STARK_WAKE)
                    .putExtra(EXTRA_WAKE_ON, nowAwake)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                context.startActivity(go)
            } catch (_: Exception) {
            }

            refreshAll(context)
        }
    }
}
```

### `android/app/src/main/java/com/jarvis/app/WakeTile.kt`

```kotlin
package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.jarvis.app.widget.StarkWidgetProvider

/** Quick Settings tile: tap to toggle the "Hey Jarvis" wake service. */
class WakeTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        try {
            if (WakeService.isRunning) {
                Store(this).wakeEnabled = false
                startService(Intent(this, WakeService::class.java).setAction(WakeService.ACTION_STOP))
            } else {
                val micOk = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val overlayOk = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
                if (micOk && overlayOk) {
                    Store(this).wakeEnabled = true
                    startForegroundService(
                        Intent(this, WakeService::class.java).setAction(WakeService.ACTION_START)
                    )
                } else {
                    val i = Intent(this, MainActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivityAndCollapse(i)
                    return
                }
            }
        } catch (_: Exception) {
        }
        StarkWidgetProvider.refreshAll(this)
        refreshReactorWidgets(this)
        refresh()
    }

    private fun refresh() {
        try {
            val t = qsTile ?: return
            t.state = if (WakeService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.label = if (WakeService.isRunning) "Jarvis on" else "Jarvis off"
            t.updateTile()
        } catch (_: Exception) {
        }
    }
}
```

## Part 2 — Resources (layouts / values / xml / drawables)

### `android/app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="widget_desc">Mini arc reactor: tap to toggle Hey Jarvis wake mode, tap while talking to interrupt.</string>
    <string name="shortcut_new_chat">New chat</string>
    <string name="shortcut_new_chat_long">Start a new chat</string>
    <string name="shortcut_briefing">Briefing</string>
    <string name="shortcut_briefing_long">Open device briefing</string>
    <string name="briefing_widget_desc">Jarvis briefing: time, battery and your next reminder.</string>
    <string name="access_desc">Jarvis screen control: read what\'s on screen, tap buttons, scroll and go back — only when you ask by voice.</string>
    <string name="stark_widget_desc">Stark reactor toggle: tap to switch Jarvis between standby and active.</string>
</resources>
```

### `android/app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0B1220</color>
</resources>
```

### `android/app/src/main/res/layout/stark_widget_layout.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@android:color/transparent"
    android:padding="8dp">

    <ImageView
        android:id="@+id/widget_reactor_icon"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:src="@android:drawable/ic_menu_mylocation"
        android:contentDescription="Stark Wake Toggle" />

    <TextView
        android:id="@+id/widget_status_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_below="@id/widget_reactor_icon"
        android:layout_centerHorizontal="true"
        android:text="STANDBY"
        android:textSize="9sp"
        android:textColor="#FBBF24"
        android:fontFamily="monospace" />
</RelativeLayout>
```

### `android/app/src/main/res/layout/widget_briefing.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bw_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#CC0B1220"
    android:gravity="center_vertical"
    android:orientation="vertical"
    android:padding="12dp">
    <TextView
        android:id="@+id/bw_time"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#F59E0B"
        android:textSize="26sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/bw_date"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#8B949E"
        android:textSize="13sp" />
    <TextView
        android:id="@+id/bw_batt"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#E6EDF3"
        android:textSize="13sp" />
    <TextView
        android:id="@+id/bw_next"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:singleLine="true"
        android:textColor="#22D3EE"
        android:textSize="13sp" />
</LinearLayout>
```

### `android/app/src/main/res/layout/widget_reactor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/transparent">
    <ImageView
        android:id="@+id/reactor_tap"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/widget_reactor"
        android:contentDescription="Jarvis wake toggle" />
</FrameLayout>
```

### `android/app/src/main/res/xml/briefing_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_briefing"
    android:description="@string/briefing_widget_desc"
    android:widgetCategory="home_screen" />
```

### `android/app/src/main/res/xml/reactor_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:targetCellWidth="1"
    android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_reactor"
    android:description="@string/widget_desc"
    android:widgetCategory="home_screen" />
```

### `android/app/src/main/res/xml/stark_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/stark_widget_layout"
    android:description="@string/stark_widget_desc"
    android:widgetCategory="home_screen" />
```

### `android/app/src/main/res/xml/shortcuts.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="new_chat"
        android:enabled="true"
        android:icon="@drawable/widget_reactor"
        android:shortcutShortLabel="@string/shortcut_new_chat"
        android:shortcutLongLabel="@string/shortcut_new_chat_long">
        <intent
            android:action="com.jarvis.app.NEW_CHAT"
            android:targetPackage="com.jarvis.app"
            android:targetClass="com.jarvis.app.MainActivity" />
    </shortcut>
    <shortcut
        android:shortcutId="briefing"
        android:enabled="true"
        android:icon="@drawable/ic_stat_jarvis"
        android:shortcutShortLabel="@string/shortcut_briefing"
        android:shortcutLongLabel="@string/shortcut_briefing_long">
        <intent
            android:action="com.jarvis.app.BRIEFING"
            android:targetPackage="com.jarvis.app"
            android:targetClass="com.jarvis.app.MainActivity" />
    </shortcut>
</shortcuts>
```

### `android/app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shots" path="." />
    <files-path name="gen" path="gen/" />
</paths>
```

### `android/app/src/main/res/xml/jarvis_access.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/access_desc"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="50"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="false" />
```

### `android/app/src/main/res/drawable/ic_stat_jarvis.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10s10,-4.47 10,-10S17.53,2 12,2z" />
</vector>
```

### `android/app/src/main/res/drawable/ic_launcher_foreground.xml`

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- outer gold ring -->
    <path
        android:strokeWidth="6"
        android:strokeColor="#F59E0B"
        android:fillColor="#00000000"
        android:pathData="M54,16 a38,38 0 1,0 0.1,0 Z" />
    <!-- cyan telemetry ring -->
    <path
        android:strokeWidth="3"
        android:strokeColor="#22D3EE"
        android:fillColor="#00000000"
        android:pathData="M54,28 a26,26 0 1,0 0.1,0 Z" />
    <!-- white-gold core -->
    <path
        android:fillColor="#FEF3C7"
        android:pathData="M54,42 a12,12 0 1,0 0.1,0 Z" />
</vector>
```

### `android/app/src/main/res/drawable/widget_reactor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <solid android:color="#0B1220" />
            <stroke android:width="3dp" android:color="#22D3EE" />
        </shape>
    </item>
    <item android:left="8dp" android:top="8dp" android:right="8dp" android:bottom="8dp">
        <shape android:shape="oval">
            <gradient
                android:type="radial"
                android:gradientRadius="24dp"
                android:startColor="#FFFFFF"
                android:centerColor="#A5F3FC"
                android:endColor="#0E7490" />
        </shape>
    </item>
</layer-list>
```

### `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

### `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

package com.jarvis.app.frontend.screens

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import com.jarvis.app.frontend.screens.StarkShareActivity
import com.jarvis.app.frontend.design.HeaderMiniReactor
import com.jarvis.app.frontend.design.hudStatusLine
import com.jarvis.app.frontend.design.AnimatedGlassBubble
import com.jarvis.app.frontend.design.ConfigPanel
import com.jarvis.app.frontend.design.HudDialog
import com.jarvis.app.frontend.design.HudTextField
import com.jarvis.app.frontend.design.GlassCircleButton
import com.jarvis.app.frontend.design.JarvisGlassDialog
import com.jarvis.app.frontend.design.JarvisGlassFill
import com.jarvis.app.frontend.design.JarvisGlassEdge
import com.jarvis.app.frontend.design.NeonPillButton
import com.jarvis.app.frontend.design.PremiumBackdrop
import com.jarvis.app.frontend.design.PremiumMuted
import com.jarvis.app.frontend.design.PremiumNeon
import com.jarvis.app.frontend.design.SwirlCore
import com.jarvis.app.frontend.design.avatarLetter
import com.jarvis.app.frontend.design.modelShortName
import com.jarvis.app.frontend.design.premiumGlass
import com.jarvis.app.frontend.design.premiumHeroVisible
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarvis.app.backend.brain.ChatData
import com.jarvis.app.backend.brain.ChatMessage
import com.jarvis.app.backend.brain.JarvisViewModel
import com.jarvis.app.backend.brain.codeShareText
import com.jarvis.app.backend.brain.formatBriefing
import com.jarvis.app.backend.brain.splitCodeBlocks
import com.jarvis.app.backend.data.dueText
import com.jarvis.app.backend.device.StarkSounds
import com.jarvis.app.backend.system.BubbleLevelBus
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
import kotlinx.coroutines.delay

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

    val bgLvl by BubbleLevelBus.level.collectAsState()
    val hudUi by HudStateBus.state.collectAsState()
    PremiumBackdrop(bgLvl) {
    // System Calibration overlay needs window-space target rects.
    var calibParent by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val calibTargets = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    var calibStep by remember { mutableStateOf(0) }
    val heroScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val heroVisible = premiumHeroVisible(vm.messages.count { it.role == "user" }, vm.busy)
    // A chat that already has messages has no hero targets - retire silently.
    if (vm.showCalib && !heroVisible) {
        LaunchedEffect(Unit) { vm.finishCalibration() }
    }
    // The mic step has no target on devices without recognition - skip it.
    LaunchedEffect(calibStep, vm.showCalib) {
        if (vm.showCalib && calibStep == 1 && !voiceAvailable(context)) calibStep = 2
    }
    // Keep the Execute step on screen on short displays.
    LaunchedEffect(calibStep, vm.showCalib) {
        if (vm.showCalib && calibStep == 2) heroScroll.animateScrollTo(heroScroll.maxValue)
    }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { calibParent = it }
    ) {
    Box(Modifier.fillMaxSize()) {
        // Scrollable content runs full-bleed; the floating island and the
        // dialogue fade the chats behind them instead of blocking them.
        Column(Modifier.fillMaxSize()) {
        LaunchedEffect(vm.messages.size, vm.busy) {
            if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
        }
        if (!heroVisible) {
            // The core stays on as an ambient backdrop behind the messages.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SwirlCore(
                    level = if (vm.listening) 1f else if (hudUi.speaking) 0.6f else 0.2f,
                    modifier = Modifier.align(Alignment.Center).alpha(0.35f),
                    diameter = 300.dp
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 88.dp, end = 12.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vm.messages, key = { it.time }) { Bubble(it, vm::retryLast, vm::speakText) }
                    if (vm.busy) {
                        item { ThinkingRow() }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 88.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.messages, key = { it.time }) { Bubble(it, vm::retryLast, vm::speakText) }
                if (vm.busy) {
                    item { ThinkingRow() }
                }
            }
        }
        if (heroVisible) {
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(heroScroll)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SwirlCore(
                    level = if (vm.listening) 1f else if (hudUi.speaking) 0.6f else 0.2f,
                    modifier = Modifier.onGloballyPositioned { calibTargets[0] = it },
                    diameter = 148.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (vm.brainOk) "SYSTEM ONLINE" else "OFFLINE MODE",
                        color = PremiumMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "What Can I Do for\nYou Today?",
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium,
                        lineHeight = 34.sp, textAlign = TextAlign.Center
                    )
                }
                Column(
                    Modifier.fillMaxWidth()
                        .premiumGlass(RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).background(PremiumNeon, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                avatarLetter(vm.masterName.ifBlank { "Master" }),
                                color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.masterName.ifBlank { "Master" },
                                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (vm.masterInstalled) "Authorized User" else "Guest",
                                color = PremiumMuted, fontSize = 11.sp
                            )
                        }
                        GlassCircleButton(onClick = { vm.showBriefing = true }) {
                            Icon(Icons.Filled.Star, contentDescription = "Briefing", tint = Color(0xFFD1D5DB), modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeTile(Icons.Filled.Shield, vm.masterInstalled, Modifier.weight(1f)) {
                            vm.setMasterUnlocked(); vm.openSettings()
                        }
                        HomeTile(Icons.Filled.Terminal, false, Modifier.weight(1f)) { vm.showHooks = true }
                        HomeTile(Icons.Filled.FolderOpen, false, Modifier.weight(1f)) { vm.showChats = true }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (vm.brainOk) "Environment initialized. Model " + modelShortName(vm.model) + " ready."
                        else "Offline mode. On-device tools ready.",
                        color = Color(0xFFD1D5DB), fontSize = 14.sp, fontWeight = FontWeight.Light,
                        lineHeight = 20.sp
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NeonPillButton("Execute Script", Modifier.onGloballyPositioned { calibTargets[2] = it }) { vm.showHooks = true }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StarterChip("What can you do?") { vm.send("What can you do?") }
                    StarterChip("Calculate 15% of 240") { vm.send("Calculate 15% of 240") }
                    StarterChip("Motivate me") { vm.send("Motivate me in one line") }
                }
                Spacer(Modifier.height(172.dp))
            }
        }
        }
        // Gradient dims: chats fade out under the floating chrome.
        BoxWithConstraints(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp)) {
            val cx = constraints.maxWidth / 2f
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.85f),
                        0.55f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                        center = Offset(cx, 0f),
                        radius = 560f
                    )
                )
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(210.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
        )
        HudIsland(
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
            onInterrupt = vm::interruptSpeech,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
        )
        Box(Modifier.align(Alignment.BottomCenter)) {
            InputRow(
                onSend = vm::send,
                onMic = ::onMicTap,
                onMicPositioned = { calibTargets[1] = it },
                micVisible = voiceAvailable(context),
                listening = vm.listening,
                heard = vm.lastHeard,
                heardFresh = vm.heardFresh,
                voiceNote = vm.voiceNote
            )
        }
    }
    if (vm.showCalib && heroVisible) {
        CalibrationOverlay(
            step = calibStep,
            rect = calibRect(calibStep, calibTargets.toMap(), calibParent),
            parent = calibParent,
            onTargetTap = { if (calibStep < 3) calibStep++ },
            onFinish = { vm.finishCalibration() }
        )
    }
    }
    }

    if (vm.showSettings) ConfigPanel(vm)
    if (vm.showChats) ChatsDialog(vm)
    if (vm.showMemory) MemoryDialog(vm)
    if (vm.showList) ListDialog(vm)
    if (vm.showOnboard) OnboardDialog(vm, ::onMicTap, { onWakeTap() })
    else if (vm.showProfile) ProfileDialog(vm)
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

/** Glass menu row (design_md navigation overlay): icon + label, green when active. */
@Composable
private fun GlassMenuRow(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (active) PremiumNeon else Color(0xFFD4D4D8)
            )
        },
        leadingIcon = {
            Icon(
                icon, contentDescription = null,
                tint = if (active) PremiumNeon else Color(0xFF71717A),
                modifier = Modifier.size(16.dp)
            )
        },
        onClick = onClick
    )
}

/** Dark glass input colors (design_md): black field, green focus ring. */
@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Black.copy(alpha = 0.7f),
    unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
    focusedBorderColor = PremiumNeon.copy(alpha = 0.6f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = PremiumNeon
)

@Composable
fun HudIsland(
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
    modifier: Modifier = Modifier
) {
    val busLvl by BubbleLevelBus.level.collectAsState()
    val wakePulse by animateFloatAsState(if (wakeOn) busLvl else 0f)
    val hud by HudStateBus.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    // Compact island: one pill, chats flow up both sides. Title opens the
    // thread directory, the dot toggles wake, the reactor swaps in to stop speech.
    Box(modifier) {
        Row(
            Modifier.premiumGlass(RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color(0xFFD1D5DB), modifier = Modifier.size(14.dp))
            }
            if (hud.speaking) {
                HeaderMiniReactor(isSpeaking = true, onInterrupt = onInterrupt)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onChats)
                ) {
                    Text("J.A.R.V.I.S", color = PremiumMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        hudStatusLine(online, wakeOn),
                        color = if (online) PremiumNeon else JarvisRed,
                        fontSize = 9.sp
                    )
                }
            }
            Box(
                Modifier.size(26.dp)
                    .clip(CircleShape)
                    .background(if (wakeOn) JarvisRed else Color(0xFF374151))
                    .graphicsLayer {
                        val sc = 1f + 0.18f * wakePulse
                        scaleX = sc
                        scaleY = sc
                    }
                    .clickable(onClick = onWake),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(8.dp)
                        .background(Color.White.copy(alpha = if (wakeOn) 0.9f else 0.35f), CircleShape)
                )
            }
            GlassCircleButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFFD1D5DB), modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = JarvisGlassFill,
            border = BorderStroke(1.dp, JarvisGlassEdge),
            modifier = Modifier.width(224.dp)
        ) {
            GlassMenuRow(Icons.Filled.Add, "+ New chat", active = true) { menuOpen = false; onNewChat() }
            GlassMenuRow(Icons.Filled.Memory, "Memory") { menuOpen = false; onMemory() }
            GlassMenuRow(Icons.Filled.List, "Lists") { menuOpen = false; onList() }
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            GlassMenuRow(
                if (ttsOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                if (ttsOn) "Voice on" else "Voice off"
            ) { menuOpen = false; onToggleTts() }
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
            color = (PremiumNeon).copy(alpha = 0.45f + 0.55f * glow),
            fontSize = 14.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(14.dp))
                .background(BotGray)
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
fun Bubble(m: ChatMessage, onRetry: () -> Unit, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val segs = remember(m.text) { splitCodeBlocks(m.text) }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.align(if (!isUser) Alignment.CenterStart else Alignment.CenterEnd)
                .then(Modifier.widthIn(max = 300.dp)),
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
                            TextButton(onClick = { shareGenImage(context, genPath) }) { Text("Share", fontSize = 12.sp, color = Color.Unspecified) }
                        }
                    }
                }
            }
            segs.forEach { s ->
                if (!s.isCode) {
                    AnimatedGlassBubble(s.text, isUser)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!isUser) {
                            TextButton(onClick = { onSpeak(s.text) }) { Text("🔊 Speak", fontSize = 12.sp, color = Color.Unspecified) }
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(s.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy", fontSize = 12.sp, color = Color.Unspecified) }
                        if (!isUser && s.text.startsWith("⚠")) {
                            TextButton(onClick = onRetry) { Text("↻ Retry", fontSize = 12.sp, color = Color.Unspecified) }
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
                            }) { Text("Copy", fontSize = 12.sp, color = Color.Unspecified) }
                            TextButton(onClick = {
                                try {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, codeShareText(s.lang, s.text))
                                    context.startActivity(Intent.createChooser(send, "Share code"))
                                } catch (_: Exception) {
                                }
                            }) { Text("Share", fontSize = 12.sp, color = Color.Unspecified) }
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
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors()
    )
}

@Composable
private fun HomeTile(icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp)
            .background(
                if (active) PremiumNeon.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                if (active) PremiumNeon.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (active) PremiumNeon else Color(0xFFD1D5DB),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun InputRow(onSend: (String) -> Unit, onMic: () -> Unit, micVisible: Boolean, listening: Boolean, heard: String, heardFresh: Boolean, voiceNote: String?, onMicPositioned: (LayoutCoordinates) -> Unit = {}) {
    var input by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val focusReq = remember { FocusRequester() }
    // No flat scrim: the pill floats over chats dimmed by the bottom gradient.
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
                color = PremiumNeon, fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .premiumGlass(RoundedCornerShape(50))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { focusReq.requestFocus() }) {
                    Icon(Icons.Filled.Keyboard, contentDescription = "Keyboard", tint = PremiumMuted)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).focusRequester(focusReq),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Light),
                    cursorBrush = SolidColor(PremiumNeon),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend(input)
                            input = ""
                        }
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) Text(
                                "Message JARVIS...", color = Color(0xFF6B7280),
                                fontSize = 15.sp, fontWeight = FontWeight.Light
                            )
                            inner()
                        }
                    }
                )
                if (micVisible) {
                    Box(
                        Modifier.size(40.dp)
                            .background(PremiumNeon.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, if (listening) JarvisRed else PremiumNeon, CircleShape)
                            .onGloballyPositioned(onMicPositioned)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMic()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Mic, contentDescription = "Mic",
                            tint = if (listening) JarvisRed else PremiumNeon
                        )
                    }
                } else {
                    IconButton(onClick = {
                        if (input.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend(input)
                            input = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = PremiumNeon)
                    }
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
fun HooksDialog(vm: JarvisViewModel) {
    val mut = Muted
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
                    fontSize = 13.sp, color = mut
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
    val mut = Muted
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
fun ChatsDialog(vm: JarvisViewModel) {
    val mut = Muted
    var q by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatData?>(null) }
    var renameText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var armDelete by remember { mutableStateOf<String?>(null) }
    JarvisGlassDialog(onDismissRequest = { vm.showChats = false }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(32.dp)
                        .background(PremiumNeon.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, PremiumNeon.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Message, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(14.dp))
                }
                Text("Chats", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { vm.exportChat() }) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            }
        }
        if (vm.chats.size > 1) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Search chats...") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = glassFieldColors(),
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
                color = mut, fontSize = 14.sp
            )
        } else {
            LazyColumn(
                Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { c ->
                    val active = c.id == vm.activeChatId
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(
                                1.dp,
                                if (active) PremiumNeon.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { vm.switchChat(c.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                c.title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = if (active) PremiumNeon else Color.White, maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier.size(6.dp)
                                        .background(if (active) PremiumNeon else Color(0xFF6B7280), CircleShape)
                                )
                                Text(
                                    "${c.msgs.count { it.r == "user" }} messages" +
                                        if (active) " • Active" else "",
                                    fontSize = 11.sp, color = Color(0xFF6B7280)
                                )
                            }
                        }
                        IconButton(onClick = { renameTarget = c; renameText = c.title }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename chat", tint = mut, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            if (armDelete == c.id) { vm.deleteChat(c.id); armDelete = null }
                            else armDelete = c.id
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = if (armDelete == c.id) "Tap again to delete" else "Delete chat",
                                tint = if (armDelete == c.id) Color.Red else mut,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                if (confirmClear) { vm.clearChats(); confirmClear = false }
                else confirmClear = true
            }) {
                Icon(
                    Icons.Filled.Delete, contentDescription = null,
                    tint = Color(0xFFEF4444).copy(alpha = 0.8f), modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (confirmClear) "Tap again to purge all" else "Purge All",
                    fontSize = 13.sp, color = Color(0xFFEF4444).copy(alpha = 0.8f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.newChat() }) {
                    Text("+ New Thread", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PremiumNeon)
                }
                TextButton(onClick = { vm.showChats = false }) {
                    Text("Close", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                }
            }
        }
    }

    if (renameTarget != null) {
        JarvisGlassDialog(onDismissRequest = { renameTarget = null }) {
            Text("Rename chat", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = renameText, onValueChange = { renameText = it },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = Color(0xFF9CA3AF))
                }
                TextButton(onClick = {
                    renameTarget?.let { vm.renameChat(it.id, renameText) }
                    renameTarget = null
                }) {
                    Text("Save", color = PremiumNeon, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun OnboardDialog(vm: JarvisViewModel, onMic: () -> Unit, onWake: () -> Unit) {
    val mut = Muted
    val context = LocalContext.current
    fun hasMic(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    var permTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            permTick++
        }
    }
    HudDialog(
        onDismissRequest = { vm.finishOnboard() },
        title = { Text("👋 Welcome to Jarvis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Three quick steps to wake me up:", fontSize = 14.sp)
                OnboardRow(done = remember(permTick) { hasMic() }, label = "Microphone for voice input", btn = "Allow", onBtn = onMic)
                OnboardRow(done = vm.wakeOn, label = "Hey Jarvis wake word + HUD bubble", btn = "Enable", onBtn = onWake)
                OnboardRow(done = remember(permTick, vm.batteryStateTick) { vm.batteryUnrestricted() },
                    label = "Unrestricted battery (survive reboot)",
                    btn = "Fix",
                    onBtn = vm::requestBatteryUnrestricted
                )
                OnboardRow(done = remember(permTick) { isAccessEnabled(context) },
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

/** Target rect in overlay-parent space (null while layout settles). Never throws. */
private fun calibRect(step: Int, targets: Map<Int, LayoutCoordinates>, parent: LayoutCoordinates?): Rect? {
    val t = targets[step] ?: return null
    val p = parent ?: return null
    return try {
        if (!t.isAttached || !p.isAttached) return null
        val o = p.boundsInWindow().topLeft
        t.boundsInWindow().translate(Offset(-o.x, -o.y))
    } catch (_: Exception) {
        null
    }
}

/**
 * System Calibration: scrim + punched spotlight + typewriter tooltip.
 * Taps inside the hole advance; everything else is consumed. SKIP is always
 * visible and a crash only restarts the tour at step 0 - never a lock.
 */
@Composable
private fun BoxScope.CalibrationOverlay(
    step: Int,
    rect: Rect?,
    parent: LayoutCoordinates?,
    onTargetTap: () -> Unit,
    onFinish: () -> Unit
) {
    val density = LocalDensity.current
    val handoff = step >= 3
    var entered by remember { mutableStateOf(false) }
    // Pitch-black entry beat, then the tour fades in.
    LaunchedEffect(Unit) { delay(1500); entered = true }
    val hole = rect?.inflate(with(density) { 14.dp.toPx() })
    val holeCornerPx = with(density) { 28.dp.toPx() }
    val pw = parent?.size?.width ?: 0
    val ph = parent?.size?.height ?: 0
    val below = hole != null && hole.bottom + 170 < ph
    val tipW = (pw - 64).coerceAtLeast(200)
    val tipY = when {
        handoff -> (ph / 2 - 90).coerceAtLeast(0)
        hole == null -> 200
        below -> (hole.bottom + 16).toInt()
        else -> (hole.top - 170).toInt().coerceAtLeast(0)
    }
    val fullText = when (step) {
        0 -> "[SYS] CALIBRATING NEURAL LINK. TAP CORE TO INITIATE."
        1 -> "[SYS] AUDIO TELEMETRY OFFLINE. TAP TO OPEN COMM CHANNEL."
        2 -> "[SYS] COMMAND TERMINAL. DEPLOY LOCAL SCRIPTS HERE."
        else -> "[SYS] CALIBRATION COMPLETE. JARVIS IS LISTENING. TAP ANYWHERE."
    }
    var shown by remember(step) { mutableStateOf(0) }
    LaunchedEffect(step, entered) {
        if (!entered) return@LaunchedEffect
        shown = 0
        while (shown < fullText.length) { delay(14); shown++ }
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(step, hole, handoff, entered) {
                detectTapGestures { off ->
                    if (!entered) return@detectTapGestures
                    if (handoff) { onFinish(); return@detectTapGestures }
                    if (hole != null && hole.contains(off)) onTargetTap()
                }
            }
    ) {
        if (!handoff) {
            Canvas(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawRect(Color.Black.copy(alpha = if (entered) 0.85f else 1f))
                if (entered && hole != null) {
                    if (step == 0) {
                        drawCircle(
                            Color.Transparent, radius = hole.width / 2f, center = hole.center,
                            blendMode = BlendMode.Clear
                        )
                    } else {
                        drawRoundRect(
                            Color.Transparent, topLeft = hole.topLeft, size = hole.size,
                            cornerRadius = CornerRadius(holeCornerPx), blendMode = BlendMode.Clear
                        )
                    }
                    // Connector stub bridging the hole toward the tooltip.
                    val from = if (below) Offset(hole.center.x, hole.bottom)
                    else Offset(hole.center.x, hole.top)
                    val dir = if (below) Offset(0f, 1f) else Offset(0f, -1f)
                    drawLine(PremiumNeon, from, from + dir * 14f, strokeWidth = 2f)
                }
            }
        }
        if (entered) {
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(32, tipY) }
                    .width(with(density) { tipW.toDp() })
                    .background(Color(0xFF111418), RoundedCornerShape(16.dp))
                    .border(1.dp, PremiumNeon, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    fullText.take(shown), color = Color.White, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 18.sp
                )
            }
        }
        TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.TopEnd)) {
            Text(
                "SKIP", fontSize = 12.sp, color = PremiumMuted,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun ProfileDialog(vm: JarvisViewModel) {
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    JarvisGlassDialog(onDismissRequest = { vm.skipProfile() }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = PremiumNeon, modifier = Modifier.size(22.dp))
            Text("Who am I serving?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Establish your identity profile. System memory will adapt to your context across all sessions.",
            color = PremiumMuted, fontSize = 13.sp, lineHeight = 18.sp
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            placeholder = { Text("Designation (e.g., Raj Thakur)") },
            singleLine = true, shape = RoundedCornerShape(12.dp), colors = glassFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = about, onValueChange = { about = it.take(200) },
            placeholder = { Text("Append system context (city, primary interests, environment…)") },
            minLines = 3, shape = RoundedCornerShape(12.dp), colors = glassFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.skipProfile() }) {
                Text("Bypass", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
            }
            NeonPillButton("Commit Profile") { vm.saveProfile(name, about) }
        }
    }
}

@Composable
private fun OnboardRow(done: Boolean, label: String, btn: String, onBtn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (done) "✓" else "○",
            color = if (done) (Good) else (Muted),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (!done) Button(onClick = onBtn) { Text(btn, fontSize = 12.sp) }
    }
}

@Composable
fun RemindersDialog(vm: JarvisViewModel) {
    val mut = Muted
    val acc = Accent
    val items = remember(vm.remTick) { vm.reminderItems() }
    val now = remember { System.currentTimeMillis() }
    var newRem by remember { mutableStateOf("") }
    var remMsg by remember { mutableStateOf("") }
    HudDialog(
        onDismissRequest = { vm.showReminders = false },
        title = { Text("⏰ Reminders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Or say: remind me in 10 minutes to stretch.", fontSize = 13.sp, color = mut)
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
    val acc = Accent
    HudDialog(
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
    val mut = Muted
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
                Text("Todos (or say “add … to my list”):", fontSize = 13.sp, color = mut)
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
    val mut = Muted
    var input by remember { mutableStateOf("") }
    val mems = remember(vm.memTick) { vm.memories() }
    HudDialog(
        onDismissRequest = { vm.showMemory = false },
        title = { Text("🧠 Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Things Jarvis remembers about you (also via “remember …” in chat):",
                    fontSize = 13.sp, color = mut
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

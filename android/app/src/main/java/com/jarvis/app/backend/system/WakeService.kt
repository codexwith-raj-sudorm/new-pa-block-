package com.jarvis.app.backend.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.jarvis.app.frontend.screens.WakeHudActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import com.jarvis.app.backend.ai.EngineVoiceInfo
import com.jarvis.app.backend.ai.personaForKey
import com.jarvis.app.backend.ai.resolvePersonaVoices
import com.jarvis.app.backend.brain.Store
import com.jarvis.app.backend.brain.hearsWakeWord
import com.jarvis.app.backend.brain.localeForListen
import com.jarvis.app.backend.data.wakeBucket
import com.jarvis.app.backend.data.wakeGreet
import com.jarvis.app.backend.data.wakeGreetStamp
import com.jarvis.app.backend.device.SoundMuter
import com.jarvis.app.backend.device.StarkSounds
import com.jarvis.app.backend.voice.VoiceCapture
import com.jarvis.app.backend.voice.VpVerdict
import com.jarvis.app.backend.voice.bestHeard
import com.jarvis.app.backend.voice.templatesFromString
import com.jarvis.app.backend.voice.verifyVoiceprint
import com.jarvis.app.backend.voice.voiceGateDecision
import com.jarvis.app.backend.voice.vpThresholdFor
import com.jarvis.app.frontend.design.HubBubble
import com.jarvis.app.frontend.design.JarvisShimejiOverlay
import com.jarvis.app.frontend.design.nearestDockSide
import com.jarvis.app.frontend.widgets.SpeechState
import com.jarvis.app.frontend.widgets.refreshReactorWidgets
import com.jarvis.app.R
import com.jarvis.app.frontend.screens.MainActivity

/**
 * Owns the "Hey Jarvis" wake loop, the floating bubble (visible over any app
 * and the home screen), and the persistent notification — so wake works even
 * with Jarvis in the background.
 */
class WakeService : Service() {

    companion object {
        const val ACTION_START = "com.jarvis.app.WAKE_START"
        const val ACTION_STOP = "com.jarvis.app.WAKE_STOP"
        const val ACTION_PAUSE = "com.jarvis.app.WAKE_PAUSE"
        const val ACTION_RESUME = "com.jarvis.app.WAKE_RESUME"
        const val ACTION_WAKE_COMMAND = "com.jarvis.app.WAKE_COMMAND"
        const val ACTION_HUSH = "com.jarvis.app.HUSH"
        private const val NOTIF_ID = 41
        private const val CHANNEL_ID = "jarvis_standby"

        @Volatile
        var isRunning = false
    }

    private lateinit var store: Store
    private lateinit var audio: AudioManager
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var wakeRecognizer: SpeechRecognizer? = null
    private var pausedByApp = false
    private var cmdRecognizer: SpeechRecognizer? = null
    private var vpCapture: VoiceCapture? = null
    private var cmdRetries = 0
    private var tts: TextToSpeech? = null
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleLifecycle: ServiceLifecycleOwner? = null
    private var shimejiView: ComposeView? = null
    private var shimejiLifecycle: ServiceLifecycleOwner? = null
    private var islandView: ComposeView? = null
    private var islandLifecycle: ServiceLifecycleOwner? = null
    private val accentOverride = kotlinx.coroutines.flow.MutableStateFlow<Color?>(null)
    private val bubbleDockCmd = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var restarts = 0
    @Suppress("DEPRECATION")
    private var callListener: PhoneStateListener? = null
    private val errorTimes = ArrayDeque<Long>()
    private var lastStandbyToastMs = 0L
    private var lastBubbleHushMs = 0L
    private var bubbleDocked = false
    private var lastBubbleTouchMs = 0L
    // Last moment the voice flags were observed quiet. A TTS/recognizer flag
    // stuck true must not veto docking forever - 45s of it counts as quiet.
    private var lastQuietMs = 0L
    private var dockCmdMs = 0L

    /** Set the dock command, stamping nonzero commands for stale detection. */
    private fun setDockCmd(side: Int) {
        if (side != 0) dockCmdMs = System.currentTimeMillis()
        bubbleDockCmd.value = side
    }
    private var started = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale.getDefault()
                } catch (_: Exception) {
                }
                applySavedVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { SpeechState.speaking = true; HudStateBus.update(speaking = true) }
                    override fun onDone(id: String?) { SpeechState.speaking = false; HudStateBus.update(speaking = false) }
                    override fun onError(id: String?) { SpeechState.speaking = false; HudStateBus.update(speaking = false) }
                })
            }
        }
        makeChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == null) {
            // Sticky restart after process death — come back if still armed.
            if (!started && store.wakeEnabled) startUp()
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_START -> startUp()
            ACTION_STOP -> shutDown(userStopped = true)
            ACTION_PAUSE -> {
                pausedByApp = true
                haltLoop()
            }
            ACTION_RESUME -> {
                pausedByApp = false
                if (started && !CallStateBus.current && !MicHandoff.appActive && wakeRecognizer == null) startWakeLoop()
            }
            ACTION_HUSH -> hushSpeech()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    // ---- lifecycle ----

    private fun startUp() {
        if (started) return
        started = true
        isRunning = true
        restarts = 0
        pausedByApp = false
        errorTimes.clear()
        try {
            val winLabel = if (store.standbyWinOn) {
                "Armed • " + fmtWindowTime(store.standbyStart) + "–" + fmtWindowTime(store.standbyEnd) + " — tap to open"
            } else {
                "Say \"Hey Jarvis\" — tap to open"
            }
            val notif = buildNotification(winLabel)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notif, 128) // FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            toast("Couldn't start listener (${e.message?.take(60)})")
            shutDown()
            return
        }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:standby").also {
                // 12h failsafe cap; the window STOP edge releases far sooner.
                it.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
            wakeLock = null
        }
        StandbyBus.set(true)
        addBubble()
        addShimeji()
        addIsland()
        muteBlip(800) // cover any start beep on arming
        refreshReactorWidgets(this)
        HudStateBus.postTicker("[SYS: ONLINE]")
        toast("Wake word on — say \"Hey Jarvis\"")
        startWakeLoop()
        startCallWatch()
        armStandbyWatchdog(this, true)
    }

    private fun shutDown(userStopped: Boolean = false) {
        started = false
        isRunning = false
        StandbyBus.set(false)
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        if (userStopped) runCatching { store.wakeEnabled = false }
        if (userStopped) runCatching { armStandbyWatchdog(this, false) }
        stopCallWatch()
        haltLoop()
        removeBubble()
        removeShimeji()
        removeIsland()
        unmute()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        refreshReactorWidgets(this)
        stopSelf()
    }

    // ---- wake loop ----

    private fun startWakeLoop() {
        if (!started || pausedByApp) return
        try {
            destroyWakeRecognizer()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                enterStandbyRetry("no recognition")
                return
            }
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            wakeRecognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    if (r !== wakeRecognizer) return
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    destroyWakeRecognizer()
                    BubbleLevelBus.reset()
                    if (!started || pausedByApp) return
                    restarts = 0
                    if (heard.any { hearsWakeWord(it) }) onWakeWord()
                    else startWakeLoop()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (r !== wakeRecognizer) return
                    val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (started && !pausedByApp && heard.any { hearsWakeWord(it) }) {
                        destroyWakeRecognizer()
                        onWakeWord()
                    }
                }

                override fun onError(error: Int) {
                    if (r !== wakeRecognizer) return
                    destroyWakeRecognizer()
                    BubbleLevelBus.reset()
                    if (!started || pausedByApp) return
                    if (CallStateBus.current) return // call watch resumes us on hangup
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartWithStormGuard()
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            if (++restarts > 10) {
                                restarts = 0
                                enterStandbyRetry("mic busy")
                            } else {
                                startWakeLoop()
                            }
                        }
                        else -> enterStandbyRetry("error $error")
                    }
                }

                override fun onEndOfSpeech() { unmute(); BubbleLevelBus.reset() }
                override fun onBeginningOfSpeech() { unmute() }
                override fun onRmsChanged(rmsdB: Float) { BubbleLevelBus.pushRms(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeForListen(store.hindiListen))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Long sessions: fewer restarts = fewer chances for any start beep.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                // §6: prefer on-device recognition — no audio leaves the phone for wake.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            muteBlip(900) // cover the start beep on EVERY restart, all streams
            r.startListening(intent)
        } catch (_: Exception) {
            destroyWakeRecognizer()
            if (started) enterStandbyRetry("mic failed")
        }
    }

    private fun onWakeWord() {
        if (!started || CallStateBus.current) return
        restarts = 0
        cmdRetries = 0
        BubbleLevelBus.reset()
        HudStateBus.postTicker("[VOICE: MATCH]")
        StarkSounds.chime()
        flashBubble()
        if (bubbleDocked) bubbleDockCmd.value = 0 // pop out on wake
        lastBubbleTouchMs = System.currentTimeMillis()
        // Instant mic: hush anything in flight and listen NOW. The greeting is
        // deferred — spoken over the Gemini round trip once the command lands,
        // or as a reprompt if the listen fails. No deaf window.
        hushSpeech()
        haltLoop() // release the mic for the handoff
        StandbyBus.override()
        strikeHaptic()
        // §6 awakening: seize the screen when the OS allows it; the app
        // session pauses us and resumes us on end. Otherwise headless.
        if (tryPopAppForWake()) return
        startCommandListen()
    }

    /**
     * Pull the app front (NEW_TASK + SINGLE_TOP onto the singleTop MainActivity).
     * True when the pop lands — the app owns the mic now. Background-start
     * blocks are normal: false keeps the headless command listen.
     */
    private fun tryPopAppForWake(): Boolean {
        return try {
            val i = Intent(this, MainActivity::class.java)
                .setAction(ACTION_WAKE_COMMAND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(i)
            pausedByApp = true
            HudStateBus.postTicker("[WAKE: POP]")
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Headless command listen: when the wake HUD can't pop over another app
     * (background-start limits), the service takes the command itself. Yields
     * instantly if the app takes the mic (pause / handoff flag).
     */
    private fun startCommandListen() {
        if (!started || CallStateBus.current || pausedByApp || MicHandoff.appActive || cmdRecognizer != null) return
        // No speaking gate: the wake greeting is deferred until the command
        // lands, so the mic opens instantly and nothing self-triggers.
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            cmdRecognizer = r
            try { vpCapture?.stop() } catch (_: Exception) { }
            vpCapture = if (vpWanted()) VoiceCapture().let { if (it.start()) it else null } else null
            HudStateBus.postTicker("[CMD: LISTENING]")
            pingNotification()
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    HudStateBus.update(listening = true)
                }

                override fun onResults(results: Bundle?) {
                    if (r !== cmdRecognizer) return
                    val pcm = try { vpCapture?.stop() } catch (_: Exception) { null }
                    vpCapture = null
                    destroyCmdRecognizer()
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
                    if (!started || pausedByApp || MicHandoff.appActive) {
                        resumeLoopAfterCmd()
                        return
                    }
                    val heard = bestHeard(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                        results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    )
                    if (heard.isEmpty()) {
                        speakWakeGreeting() // reprompt: nothing heard
                        resumeLoopAfterCmd()
                        return
                    }
                    restarts = 0
                    HudStateBus.postTicker("[CMD: HEARD]")
                    Thread({
                        val guard = store.masterKey.isNotBlank() && store.voiceGuard
                        val enrolled = store.vp_base > 0f &&
                            (templatesFromString(store.vp_templates)?.size ?: 0) == 3
                        val verdict = try {
                            if (guard && enrolled) verifyVoiceprint(
                                pcm ?: ShortArray(0),
                                templatesFromString(store.vp_templates) ?: emptyList(),
                                vpThresholdFor(store.vp_base, store.vp_mult)
                            ) else VpVerdict.UNKNOWN
                        } catch (_: Exception) {
                            VpVerdict.UNKNOWN
                        }
                        main.post {
                            try {
                                val vm = sharedJarvisVm(application)
                                vm.lastHeard = heard
                                vm.heardFresh = true
                                speakWakeGreeting() // ack now, reply lands over it
                                val d = voiceGateDecision(heard, guard, deviceLocked(), store.masterName, enrolled, verdict)
                                if (d.send) vm.send(d.cleaned, fromVoice = true, idChecked = d.bypassGuard)
                                else if (d.note != null) {
                                    vm.voiceNote = d.note
                                    HudStateBus.postTicker("[VOICE: REJECTED]")
                                }
                            } catch (_: Exception) {
                            }
                            resumeLoopAfterCmd()
                        }
                    }, "VpVerify").also { it.isDaemon = true; it.start() }
                }

                override fun onError(error: Int) {
                    if (r !== cmdRecognizer) return
                    destroyCmdRecognizer()
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
                    if (!started || pausedByApp || CallStateBus.current) return
                    if (MicHandoff.appActive) return // app owns the mic now
                    if ((error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && ++cmdRetries <= 1
                    ) {
                        startCommandListen() // one more chance before giving up
                        return
                    }
                    if (error != SpeechRecognizer.ERROR_CLIENT) {
                        HudStateBus.postTicker("[CMD: RETRY]")
                        speakWakeGreeting() // reprompt after a failed listen
                    }
                    resumeLoopAfterCmd()
                }

                override fun onEndOfSpeech() { BubbleLevelBus.reset() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { BubbleLevelBus.pushRms(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeForListen(store.hindiListen))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            muteBlip(900)
            r.startListening(intent)
        } catch (_: Exception) {
            destroyCmdRecognizer()
            HudStateBus.update(listening = false)
        }
    }

    /** Back to the wake loop once the command round-trip goes quiet. */
    private fun resumeLoopAfterCmd(tries: Int = 0) {
        if (!started || pausedByApp || CallStateBus.current || MicHandoff.appActive) return
        if (tries > 45) {
            if (wakeRecognizer == null) startWakeLoop()
            return
        }
        val busyNow = try {
            sharedJarvisVm(application).busy
        } catch (_: Exception) {
            false
        }
        if (!busyNow && !SpeechState.speaking) {
            if (wakeRecognizer == null) startWakeLoop()
            return
        }
        main.postDelayed({ resumeLoopAfterCmd(tries + 1) }, 1000)
    }

    /** Restart with storm guard: hot error loops cool down 60s. */
    private fun restartWithStormGuard() {
        val now = System.currentTimeMillis()
        while (errorTimes.isNotEmpty() && now - errorTimes.first() > 60_000) errorTimes.removeFirst()
        errorTimes.addLast(now)
        val backoff = standbyBackoffMs(errorTimes.size)
        if (backoff > 0) {
            HudStateBus.postTicker("[STANDBY: COOLING]")
            main.postDelayed({ if (started && !CallStateBus.current && !pausedByApp) startWakeLoop() }, backoff)
        } else {
            startWakeLoop()
        }
    }

    /**
     * 24x7 path: never die on transient failures. Stay alive mic-off and let
     * the watchdog resume the loop (5-min retry + 15-min heartbeat).
     */
    private fun enterStandbyRetry(reason: String) {
        haltLoop()
        HudStateBus.postTicker("[STANDBY: PAUSED]")
        val now = System.currentTimeMillis()
        if (shouldStandbyToast(now, lastStandbyToastMs)) {
            lastStandbyToastMs = now
            toast("Wake paused ($reason) — resumes automatically")
        }
        armStandbyRetry(this)
    }

    // ---- call awareness (pause the loop off-hook, resume on hangup) ----

    private fun startCallWatch() {
        stopCallWatch()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> if (CallStateBus.current) {
                            CallStateBus.set(false)
                            HudStateBus.postTicker("[CALL: END]")
                            if (started) {
                                addBubble()
                                addShimeji()
                            }
                            if (started && !pausedByApp && wakeRecognizer == null && !MicHandoff.appActive) startWakeLoop()
                        }
                        else -> if (!CallStateBus.current) {
                            CallStateBus.set(true)
                            HudStateBus.postTicker("[CALL: PAUSED]")
                            haltLoop()
                            removeBubble()
                            removeShimeji()
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            callListener = l
        } catch (_: Exception) {
        }
    }

    private fun stopCallWatch() {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION")
            callListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        } catch (_: Exception) {
        }
        callListener = null
    }

    private fun haltLoop() {
        try {
            wakeRecognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            cmdRecognizer?.cancel()
        } catch (_: Exception) {
        }
        destroyWakeRecognizer()
        destroyCmdRecognizer()
    }

    /** Voiceprint active for headless commands: guard on + valid enrollment. */
    private fun vpWanted(): Boolean {
        if (!started) return false
        if (store.masterKey.isBlank() || !store.voiceGuard) return false
        if (store.vp_base <= 0f) return false
        return (templatesFromString(store.vp_templates)?.size ?: 0) == 3
    }

    private fun destroyCmdRecognizer() {
        try { vpCapture?.stop() } catch (_: Exception) { }
        vpCapture = null
        try {
            cmdRecognizer?.destroy()
        } catch (_: Exception) {
        }
        cmdRecognizer = null
    }

    private fun destroyWakeRecognizer() {
        try {
            wakeRecognizer?.destroy()
        } catch (_: Exception) {
        }
        wakeRecognizer = null
    }

    private fun openAppForCommand() {
        try {
            val i = Intent(this, WakeHudActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {
            // Background start blocked — user taps the bubble/notification instead.
            flashBubble()
            pingNotification()
        }
    }

    private fun deviceLocked(): Boolean = try {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        km.isKeyguardLocked
    } catch (_: Exception) {
        false
    }

    private fun speakWakeGreeting() {
        if (!store.ttsEnabled) return
        if (store.masterKey.isNotBlank() && store.voiceGuard && deviceLocked()) {
            try {
                tts?.speak("State your name.", TextToSpeech.QUEUE_FLUSH, null, "wake")
            } catch (_: Exception) {
            }
            return
        }
        try {
            val now = java.time.LocalDateTime.now()
            val stamp = wakeGreetStamp(now.toLocalDate().toString(), wakeBucket(now.hour))
            if (store.masterKey.isNotBlank() && store.lastWakeGreet != stamp) {
                store.lastWakeGreet = stamp
                tts?.speak(
                    wakeGreet(now.hour, store.masterName),
                    TextToSpeech.QUEUE_FLUSH, null, "wake"
                )
            } else {
                tts?.speak("Yes sir?", TextToSpeech.QUEUE_FLUSH, null, "wake")
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Arc-reactor bubble tap: while Jarvis speaks it stops the response
     * (service TTS + in-app speech); otherwise it opens the app for a command.
     */
    private fun onBubbleTap() {
        StarkSounds.click()
        val now = System.currentTimeMillis()
        lastBubbleTouchMs = now
        if (bubbleDocked) {
            bubbleDockCmd.value = 0 // tap only pops the bubble back out
            return
        }
        if (SpeechState.speaking) {
            lastBubbleHushMs = now
            hushSpeech()
            InterruptBus.request()
            HudStateBus.postTicker("[INTERRUPT]")
        } else if (now - lastBubbleHushMs < 600) {
            // Second tap of a hush double-tap — swallow so the app doesn't pop open.
        } else {
            openAppForCommand()
            cmdRetries = 0
            startCommandListen()
        }
    }

    /** Double-tap: stop the response and switch wake mode off (re-arm from the app). */
    private fun onBubbleDoubleTap() {
        StarkSounds.click()
        lastBubbleTouchMs = System.currentTimeMillis()
        hushSpeech()
        InterruptBus.request()
        toast("Wake off — re-arm it from the app")
        shutDown(userStopped = true)
    }

    /** Long-press: pop the bubble out (if docked) and open the wake screen. */
    private fun onBubbleHold() {
        StarkSounds.click()
        lastBubbleTouchMs = System.currentTimeMillis()
        if (bubbleDocked) bubbleDockCmd.value = 0
        openAppForCommand()
    }

    private fun hushSpeech() {
        SpeechState.speaking = false
        runCatching { tts?.stop() }
    }

    private fun applySavedVoice() {
        val t = tts ?: return
        try {
            val key = "priya" // fixed voice
            val persona = personaForKey(key)
            val all = try { t.voices } catch (_: Exception) { null }.orEmpty()
            val loc = Locale.getDefault()
            val infos = all.map {
                EngineVoiceInfo(it.name, it.locale?.language ?: "", it.locale?.country ?: "", it.isNetworkConnectionRequired, it.features?.toSet().orEmpty())
            }
            val want = resolvePersonaVoices(infos, loc.language, loc.country ?: "")[key]
            val match = all.firstOrNull { it.name == want?.name }
            if (match != null) t.voice = match
            else t.language = loc
            t.setSpeechRate(0.93f) // fixed
            t.setPitch(0.68f) // fixed
        } catch (_: Exception) {
        }
    }

    /** Idle watch: dock the bubble to the nearest edge after 45s quiet. The wake loop keeps listening. */
    private val idleDockCheck = object : Runnable {
        override fun run() {
            try {
                val nowMs = System.currentTimeMillis()
                if (bubbleDockCmd.value != 0 && !bubbleDocked && nowMs - dockCmdMs > 15_000) {
                    bubbleDockCmd.value = 0 // stale command (lost race) - retry next cycle
                }
                if (!SpeechState.speaking && !HudStateBus.state.value.listening) lastQuietMs = nowMs
                if (started && bubbleView != null && !bubbleDocked && bubbleDockCmd.value == 0 &&
                    nowMs - lastBubbleTouchMs > 45_000 &&
                    nowMs - lastQuietMs > 45_000
                ) {
                    val p = bubbleParams
                    if (p != null) {
                        val dm = resources.displayMetrics
                        setDockCmd(nearestDockSide(p.x.toFloat(), 60f * dm.density, dm.widthPixels.toFloat()))
                    }
                }
            } catch (_: Exception) {
            }
            if (started && bubbleView != null) main.postDelayed(this, 5000)
        }
    }

    // ---- floating bubble (over any app + home screen) ----

    private fun addBubble() {
        if (bubbleView != null) return
        try {
            val owner = ServiceLifecycleOwner()
            owner.handleCreate()
            bubbleLifecycle = owner
            val view = ComposeView(this)
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            val wPx = (120 * resources.displayMetrics.density).toInt()
            val hPx = (104 * resources.displayMetrics.density).toInt()
            val p = WindowManager.LayoutParams(
                wPx, hPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = 24
            p.y = 320
            bubbleParams = p
            view.setContent {
                val level by BubbleLevelBus.level.collectAsState()
                val hud by HudStateBus.state.collectAsState()
                val tick by HudStateBus.ticker.collectAsState()
                val flash by accentOverride.collectAsState()
                val accent = flash ?: Color(
                    hudAccentArgb(hud.listening, hud.thinking, hud.speaking, hud.online)
                )
                var tickVisible by remember { mutableStateOf(false) }
                LaunchedEffect(tick) {
                    if (tick != null) {
                        tickVisible = true
                        delay(2000)
                        tickVisible = false
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val s = 1f + 0.22f * level
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }
                    ) {
                        HubBubble(
                            startX = p.x.toFloat(),
                            startY = p.y.toFloat(),
                            contentWidthDp = 120f,
                            listening = hud.listening,
                            speaking = hud.speaking,
                            wakeFlash = flash != null,
                            onClick = { onBubbleTap() },
                            onDoubleTap = { onBubbleDoubleTap() },
                            onLongPress = { onBubbleHold() },
                            onDragStart = {
                                lastBubbleTouchMs = System.currentTimeMillis()
                                if (bubbleDockCmd.value != 0) bubbleDockCmd.value = 0
                            },
                            onEdgeRelease = { side ->
                                lastBubbleTouchMs = System.currentTimeMillis()
                                if (!bubbleDocked) setDockCmd(side)
                            },
                            dockCmd = bubbleDockCmd.collectAsState().value,
                            onDockedChange = { bubbleDocked = it },
                            onPositionChanged = { nx, ny ->
                                if (bubbleDockCmd.value == 0 && !bubbleDocked) {
                                    lastBubbleTouchMs = System.currentTimeMillis()
                                }
                                p.x = nx.toInt()
                                p.y = ny.toInt()
                                runCatching { wm.updateViewLayout(view, p) }
                            }
                        )
                    }
                    AnimatedVisibility(
                        visible = tickVisible && tick != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            tick?.text.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = accent,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(Color(0xFF0B1220).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            wm.addView(view, p)
            bubbleView = view
            bubbleDocked = false
            bubbleDockCmd.value = 0
            lastBubbleTouchMs = System.currentTimeMillis()
            lastQuietMs = lastBubbleTouchMs
            main.removeCallbacks(idleDockCheck)
            main.postDelayed(idleDockCheck, 5000)
            owner.handleResume()
        } catch (_: Exception) {
            bubbleView = null
        }
    }

    private fun removeBubble() {
        val v = bubbleView ?: return
        bubbleView = null
        main.removeCallbacks(idleDockCheck)
        bubbleDockCmd.value = 0
        bubbleDocked = false
        runCatching { wm.removeView(v) }
        runCatching { bubbleLifecycle?.handleDestroy() }
        bubbleLifecycle = null
        bubbleParams = null
    }

    // ---- Shimeji companion (system-wide, camera-free) ----

    /**
     * Adds a non-touchable companion beside the existing wake bubble. It uses
     * the same foreground service and overlay permission, so no second service,
     * camera permission, CameraX, or MediaPipe runtime is needed.
     */
    private fun addShimeji() {
        if (shimejiView != null) return
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
            val owner = ServiceLifecycleOwner()
            owner.handleCreate()
            val view = ComposeView(this)
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            val density = resources.displayMetrics.density
            val widthPx = (176 * density).toInt()
            val heightPx = (160 * density).toInt()
            val marginPx = (12 * density).toInt()
            val bottomInsetPx = (92 * density).toInt()
            val dm = resources.displayMetrics
            val p = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = (dm.widthPixels - widthPx - marginPx).coerceAtLeast(0)
            p.y = (dm.heightPixels - heightPx - bottomInsetPx).coerceAtLeast(0)
            view.setContent { JarvisShimejiOverlay() }
            wm.addView(view, p)
            shimejiView = view
            shimejiLifecycle = owner
            owner.handleResume()
        } catch (_: Exception) {
            shimejiView = null
            shimejiLifecycle = null
        }
    }

    private fun removeShimeji() {
        val v = shimejiView ?: return
        shimejiView = null
        runCatching { wm.removeView(v) }
        runCatching { shimejiLifecycle?.handleDestroy() }
        shimejiLifecycle = null
    }

    // ---- standby island (§8): cutout-tethered arm indicator ----

    private fun addIsland() {
        if (islandView != null) return
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
            val owner = ServiceLifecycleOwner()
            owner.handleCreate()
            val view = ComposeView(this)
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            val hPx = (120 * resources.displayMetrics.density).toInt()
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, hPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                p.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            view.setContent { StandbyIslandHud(cutoutBias(wm)) }
            wm.addView(view, p)
            islandView = view
            islandLifecycle = owner
            owner.handleResume()
        } catch (_: Exception) {
            islandView = null
            islandLifecycle = null
        }
    }

    private fun removeIsland() {
        val v = islandView ?: return
        islandView = null
        runCatching { wm.removeView(v) }
        runCatching { islandLifecycle?.handleDestroy() }
        islandLifecycle = null
    }

    /** Wake strike haptic: sharp heavy click as the island bursts open. */
    private fun strikeHaptic() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= 29) {
                vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(60)
            }
        } catch (_: Exception) {
        }
    }

    private fun flashBubble() {
        accentOverride.value = Color(0xFF3FB950)
        main.postDelayed({ if (started) accentOverride.value = null }, 1500)
    }


    // ---- notification ----

    private fun makeChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // v6.16 channel was IMPORTANCE_LOW (fixed at creation) — drop it
                // so the stealth channel below actually takes effect on upgrade.
                runCatching { m.deleteNotificationChannel("jarvis_wake") }
                m.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Jarvis standby", NotificationManager.IMPORTANCE_MIN)
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun openIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).setAction(ACTION_WAKE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopIntent(): PendingIntent {
        val i = Intent(this, WakeService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this, 2, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setColor(0xFF3FB950.toInt())
            .setContentTitle("Jarvis standby")
            .setContentText(text)
            .setContentIntent(openIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun pingNotification() {
        try {
            val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            m.notify(NOTIF_ID, buildNotification("Listening… speak now (or tap to open)"))
        } catch (_: Exception) {
        }
    }

    // ---- misc ----

    /** Mute ALL non-critical streams (Google's beep routes differently per OEM). */
    private fun muteBlip(ms: Long) {
        SoundMuter.mute(audio)
        main.postDelayed({ unmute() }, ms)
    }

    private fun unmute() {
        SoundMuter.unmute(audio)
    }

    private fun toast(msg: String) {
        main.post {
            try {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }
}

/** Minimal lifecycle owner so a ComposeView can live inside a Service. */
private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedController.savedStateRegistry
    fun handleCreate() {
        savedController.performAttach()
        savedController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun handleResume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun handleDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

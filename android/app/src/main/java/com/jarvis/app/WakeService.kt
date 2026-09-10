package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

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
        const val ACTION_BUBBLE_RED = "com.jarvis.app.BUBBLE_RED"
        const val ACTION_BUBBLE_BLUE = "com.jarvis.app.BUBBLE_BLUE"
        private const val NOTIF_ID = 41
        private const val CHANNEL_ID = "jarvis_wake"

        @Volatile
        var isRunning = false
    }

    private lateinit var store: Store
    private lateinit var audio: AudioManager
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var wakeRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var bubble: TextView? = null
    private var restarts = 0
    private var started = false

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
            }
        }
        makeChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startUp()
            ACTION_STOP -> shutDown()
            ACTION_PAUSE -> haltLoop()
            ACTION_RESUME -> if (started) startWakeLoop()
            ACTION_BUBBLE_RED -> tintBubble(0xFFE5484D.toInt())
            ACTION_BUBBLE_BLUE -> tintBubble(0xFF1F6FEB.toInt())
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
        try {
            val notif = buildNotification("Say \"Hey Jarvis\" — tap to open")
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
        addBubble()
        muteBlip(800) // cover any start beep on arming
        toast("Wake word on — say \"Hey Jarvis\"")
        startWakeLoop()
    }

    private fun shutDown() {
        started = false
        isRunning = false
        haltLoop()
        removeBubble()
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
        stopSelf()
    }

    // ---- wake loop ----

    private fun startWakeLoop() {
        if (!started) return
        try {
            destroyWakeRecognizer()
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                toast("Voice input not available on this device")
                shutDown()
                return
            }
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            wakeRecognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    destroyWakeRecognizer()
                    BubbleLevelBus.reset()
                    calmBubble()
                    if (!started) return
                    restarts = 0
                    if (heard.any { hearsWakeWord(it) }) onWakeWord()
                    else startWakeLoop()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (started && heard.any { hearsWakeWord(it) }) {
                        destroyWakeRecognizer()
                        onWakeWord()
                    }
                }

                override fun onError(error: Int) {
                    destroyWakeRecognizer()
                    BubbleLevelBus.reset()
                    calmBubble()
                    if (!started) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> startWakeLoop()
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            if (++restarts > 10) {
                                toast("Wake word stopped (mic busy)")
                                shutDown()
                            } else {
                                startWakeLoop()
                            }
                        }
                        else -> {
                            toast("Wake word stopped (error $error)")
                            shutDown()
                        }
                    }
                }

                override fun onEndOfSpeech() { unmute(); calmBubble(); BubbleLevelBus.reset() }
                override fun onBeginningOfSpeech() { unmute() }
                override fun onRmsChanged(rmsdB: Float) { pulseBubble(rmsdB); BubbleLevelBus.pushRms(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Long sessions: fewer restarts = fewer chances for any start beep.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60_000)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }
            muteBlip(900) // cover the start beep on EVERY restart, all streams
            r.startListening(intent)
        } catch (_: Exception) {
            destroyWakeRecognizer()
            if (started) {
                toast("Couldn't start wake-word listening")
                shutDown()
            }
        }
    }

    private fun onWakeWord() {
        if (!started) return
        restarts = 0
        BubbleLevelBus.reset()
        calmBubble()
        flashBubble()
        speakYes()
        openAppForCommand()
    }

    private fun haltLoop() {
        try {
            wakeRecognizer?.cancel()
        } catch (_: Exception) {
        }
        destroyWakeRecognizer()
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
            val i = Intent(this, MainActivity::class.java)
                .setAction(ACTION_WAKE_COMMAND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {
            // Background start blocked — user taps the bubble/notification instead.
            flashBubble()
            pingNotification()
        }
    }

    private fun speakYes() {
        if (!store.ttsEnabled) return
        try {
            tts?.speak("Yes?", TextToSpeech.QUEUE_FLUSH, null, "wake")
        } catch (_: Exception) {
        }
    }

    private fun applySavedVoice() {
        val t = tts ?: return
        try {
            val saved = store.ttsVoice
            val match = t.voices?.firstOrNull { it.name == saved }
            if (match != null) t.voice = match
            else t.language = Locale.getDefault()
            t.setSpeechRate(0.95f)
            t.setPitch(0.9f)
        } catch (_: Exception) {
        }
    }

    // ---- floating bubble (over any app + home screen) ----

    private fun addBubble() {
        if (bubble != null) return
        try {
            val size = (60 * resources.displayMetrics.density).toInt()
            val tv = TextView(this).apply {
                text = "J"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF1F6FEB.toInt())
                }
                elevation = 8f
            }
            val p = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            p.x = 24
            p.y = 320
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            tv.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        startX = p.x
                        startY = p.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (!moved && kotlin.math.abs(dx) + kotlin.math.abs(dy) > 16) moved = true
                        if (moved) {
                            p.x = startX + dx
                            p.y = startY + dy
                            try {
                                wm.updateViewLayout(tv, p)
                            } catch (_: Exception) {
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) v.performClick()
                        true
                    }
                    else -> false
                }
            }
            tv.setOnClickListener { openAppForCommand() }
            wm.addView(tv, p)
            bubble = tv
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun removeBubble() {
        val b = bubble ?: return
        bubble = null
        try {
            wm.removeView(b)
        } catch (_: Exception) {
        }
    }

    private fun tintBubble(color: Int) {
        main.post {
            try {
                (bubble?.background as? GradientDrawable)?.setColor(color)
            } catch (_: Exception) {
            }
        }
    }

    private fun flashBubble() {
        tintBubble(0xFF3FB950.toInt())
        main.postDelayed({
            if (started) tintBubble(0xFF1F6FEB.toInt())
        }, 1500)
    }

    /** Gemini-style reaction: bubble grows/glows with real mic level, still in silence. */
    private fun pulseBubble(rmsdB: Float) {
        val b = bubble ?: return
        val lvl = normalizeRms(rmsdB)
        b.scaleX = 1f + 0.35f * lvl
        b.scaleY = 1f + 0.35f * lvl
        b.elevation = 8f + 18f * lvl
    }

    private fun calmBubble() {
        val b = bubble ?: return
        b.scaleX = 1f
        b.scaleY = 1f
        b.elevation = 8f
    }

    // ---- notification ----

    private fun makeChannel() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                m.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Jarvis wake word", NotificationManager.IMPORTANCE_LOW)
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
            .setColor(0xFF1F6FEB.toInt())
            .setContentTitle("Jarvis is listening")
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
            m.notify(NOTIF_ID, buildNotification("Heard you — tap to talk"))
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

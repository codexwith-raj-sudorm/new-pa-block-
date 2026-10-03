package com.jarvis.app.backend.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.app.BuildConfig
import com.jarvis.app.backend.ai.EngineVoiceInfo
import com.jarvis.app.backend.ai.personaForKey
import com.jarvis.app.backend.ai.resolvePersonaVoices
import com.jarvis.app.backend.brain.Store
import com.jarvis.app.backend.brain.unobscureKey
import com.jarvis.app.frontend.widgets.SpeechState
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** §7 proactive intelligence: event-driven reflexes with a dynamic persona voice. */

const val KIND_BATTERY = "battery"
const val KIND_MESSAGE = "message"

const val PROACTIVE_BATTERY_COOLDOWN_MS = 6 * 60 * 60 * 1000L
const val PROACTIVE_MSG_COOLDOWN_MS = 30 * 60 * 1000L
const val PROACTIVE_FLOOR_MS = 60_000L

private val MSG_PKGS = setOf(
    "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.plus",
    "com.google.android.gm", "com.google.android.apps.messaging", "com.android.mms",
    "com.samsung.android.messaging", "org.thoughtcrime.securesms"
)

/** Pure: is this a messaging-app notification worth announcing? Tested. */
fun isMessageNotif(pkg: String): Boolean = pkg in MSG_PKGS

/**
 * Pure: cooldown gate. A 60s global floor stops storms; a new key speaks
 * past it, a repeat key waits out [cooldownMs]. Tested.
 */
fun proactiveCooldownOk(
    nowMs: Long, lastAt: Long, lastKey: String, key: String, cooldownMs: Long
): Boolean {
    if (nowMs - lastAt < PROACTIVE_FLOOR_MS) return false
    if (key != lastKey) return true
    return nowMs - lastAt >= cooldownMs
}

/** Pure: speak only when no guardrail objects. Tested. */
fun shouldSpeakProactive(
    dndBlocked: Boolean, pocketNoBt: Boolean, inCall: Boolean,
    speaking: Boolean, appBusy: Boolean
): Boolean = !dndBlocked && !pocketNoBt && !inCall && !speaking && !appBusy

/**
 * Pure: offline fallback lines (slot 0..2) so reflexes sound alive with no
 * connection. Battery gets the level, messages get the sender. Tested.
 */
fun proactiveFallback(kind: String, arg: String, slot: Int): String {
    val s = slot.mod(3)
    return if (kind == KIND_BATTERY) {
        when (s) {
            0 -> "Sir, power reserves have dropped to $arg percent. You may want to connect a charger soon."
            1 -> "A quick heads-up, sir — battery is at $arg percent and falling."
            else -> "Sir, we are at $arg percent power. Shall I dim the lights on non-essentials?"
        }
    } else {
        when (s) {
            0 -> "Sir, new message from $arg."
            1 -> "Incoming message, sir — $arg is trying to reach you."
            else -> "You have a new message from $arg, sir."
        }
    }
}

/** Pure: hidden persona prompt for the background Gemini call. Tested. */
fun proactivePrompt(kind: String, detail: String, name: String): String {
    val who = if (name == "sir") "Address him as sir" else "His name is $name — address him as sir"
    return "[SYSTEM EVENT ($kind): $detail. Generate a single, brief, calm sentence alerting him. $who. Do not use emojis.]"
}

/** Do Not Disturb (or any filter below ALL) holds all reflex speech. */
fun isDndBlocking(ctx: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT < 23) false
        else {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }
    } catch (_: Exception) {
        false
    }
}

/** Bluetooth audio route live (permission-free legacy query). */
fun isBtAudioOn(ctx: Context): Boolean {
    return try {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        audio.isBluetoothA2dpOn || audio.isBluetoothScoOn
    } catch (_: Exception) {
        false
    }
}

/** One-shot proximity read (main thread): near = pocketed. False when unsure. */
fun isPocketedOnce(ctx: Context, cb: (Boolean) -> Unit) {
    try {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (prox == null) {
            cb(false)
            return
        }
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (finished) return
                finished = true
                runCatching { sm.unregisterListener(this) }
                val v = e.values.firstOrNull() ?: -1f
                cb(v >= 0f && v < prox.maximumRange - 0.01f)
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, prox, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed({
            if (finished) return@postDelayed
            finished = true
            runCatching { sm.unregisterListener(listener) }
            cb(false)
        }, 1500)
    } catch (_: Exception) {
        cb(false)
    }
}

/** Blocking single-shot Gemini utterance for background reflexes. Null on any failure. */
fun geminiUtterance(key: String, model: String, prompt: String): String? {
    return try {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val body = JSONObject()
            .put(
                "contents", JSONArray().put(
                    JSONObject().put(
                        "parts", JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            ).toString()
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return null
            JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?.optJSONObject(0)?.optString("text")
        }
    } catch (_: Exception) {
        null
    }
}

/** Persona line if the brain is reachable, offline fallback otherwise. Runs off-main. */
fun resolveProactiveText(appCtx: Context, kind: String, detail: String, arg: String): String {
    return try {
        val store = Store(appCtx)
        val name = store.masterName.ifBlank { store.userName }.ifBlank { "sir" }
        val key = store.apiKey.ifBlank {
            if (BuildConfig.DEBUG && store.masterKey.isNotBlank()) unobscureKey(BuildConfig.DEFAULT_GEMINI_KEY) else ""
        }
        val g = if (key.isNotBlank()) {
            geminiUtterance(key, store.model, proactivePrompt(kind, detail, name))
                ?.take(280)?.trim()?.takeIf { it.isNotEmpty() }
        } else null
        g ?: proactiveFallback(kind, arg, kotlin.random.Random.nextInt(3))
    } catch (_: Exception) {
        proactiveFallback(kind, arg, 0)
    }
}

/**
 * Reflex speaker: ducks music (TRANSIENT_MAY_DUCK), speaks in the fixed
 * Jarvis voice, releases everything on done. Own TTS instance — works with
 * the app dead and the wake service off.
 */
fun speakProactive(ctx: Context, text: String) {
    try {
        val appCtx = ctx.applicationContext
        val audio = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var tts: TextToSpeech? = null
        var focusReq: AudioFocusRequest? = null
        var done = false
        fun finish() {
            if (done) return
            done = true
            try {
                focusReq?.let { audio.abandonAudioFocusRequest(it) }
            } catch (_: Exception) {
            }
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
            SpeechState.speaking = false
            HudStateBus.update(speaking = false)
        }
        try {
            focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setOnAudioFocusChangeListener { }.build()
            audio.requestAudioFocus(focusReq!!)
        } catch (_: Exception) {
            focusReq = null
        }
        SpeechState.speaking = true
        HudStateBus.update(speaking = true)
        HudStateBus.postTicker("[REFLEX]")
        tts = TextToSpeech(appCtx) { status ->
            if (status != TextToSpeech.SUCCESS) {
                finish()
                return@TextToSpeech
            }
            try {
                val t = tts ?: run {
                    finish()
                    return@TextToSpeech
                }
                val loc = Locale.getDefault()
                val all = try {
                    t.voices
                } catch (_: Exception) {
                    null
                }.orEmpty()
                val infos = all.map {
                    EngineVoiceInfo(
                        it.name, it.locale?.language ?: "", it.locale?.country ?: "",
                        it.isNetworkConnectionRequired, it.features?.toSet().orEmpty()
                    )
                }
                val want = resolvePersonaVoices(infos, loc.language, loc.country ?: "")["priya"]
                all.firstOrNull { it.name == want?.name }?.let { t.voice = it } ?: run { t.language = loc }
                t.setSpeechRate(0.93f)
                t.setPitch(0.68f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        finish()
                    }

                    override fun onError(id: String?) {
                        finish()
                    }
                })
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reflex")
            } catch (_: Exception) {
                finish()
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 30_000)
    } catch (_: Exception) {
    }
}

/**
 * Reflex entry: guardrails (toggle, cooldown, DND, call, speech, pocket),
 * then persona resolve off-main and a ducked utterance. Main thread only.
 */
fun fireProactive(
    ctx: Context, kind: String, key: String, detail: String, arg: String, cooldownMs: Long
) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Handler(Looper.getMainLooper()).post { fireProactive(ctx, kind, key, detail, arg, cooldownMs) }
        return
    }
    try {
        val appCtx = ctx.applicationContext
        val store = Store(appCtx)
        if (!store.proactiveOn) return
        val now = System.currentTimeMillis()
        if (!proactiveCooldownOk(now, store.proactiveLastAt, store.proactiveLastKey, key, cooldownMs)) return
        isPocketedOnce(appCtx) { pocketed ->
            try {
                val ok = shouldSpeakProactive(
                    isDndBlocking(appCtx),
                    pocketed && !isBtAudioOn(appCtx),
                    CallStateBus.current,
                    SpeechState.speaking,
                    MicHandoff.appActive
                )
                if (!ok) {
                    HudStateBus.postTicker("[REFLEX: HELD]")
                    return@isPocketedOnce
                }
                store.proactiveLastAt = now
                store.proactiveLastKey = key
                Thread({
                    val text = resolveProactiveText(appCtx, kind, detail, arg)
                    Handler(Looper.getMainLooper()).post { speakProactive(appCtx, text) }
                }, "ProactiveResolve").also { it.isDaemon = true; it.start() }
            } catch (_: Exception) {
            }
        }
    } catch (_: Exception) {
    }
}

/** Manifest-declared: BATTERY_LOW wakes the reflex even with the app dead. */
class BatteryReflexReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_LOW) return
        try {
            val appCtx = context.applicationContext
            val sticky = appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val pct = try {
                val l = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val s = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (l >= 0 && s > 0) (l * 100 / s) else 15
            } catch (_: Exception) {
                15
            }
            fireProactive(
                appCtx, KIND_BATTERY, "batt", "Battery low at $pct percent",
                "$pct", PROACTIVE_BATTERY_COOLDOWN_MS
            )
        } catch (_: Exception) {
        }
    }
}

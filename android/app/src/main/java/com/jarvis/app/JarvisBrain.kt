package com.jarvis.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// ---------- models ----------

data class ChatMessage(val role: String, val text: String) // role: user | bot

data class ChatData(val id: String, var title: String, val msgs: MutableList<Pair<String, String>>)

data class TtsVoice(val id: String, val label: String)

/** Chat list title = first user message, truncated. Pure, tested. */
fun chatTitle(msgs: List<Pair<String, String>>): String {
    val first = msgs.firstOrNull { it.first == "user" }?.second?.trim().orEmpty()
    if (first.isEmpty()) return "New chat"
    return if (first.length <= 32) first else first.take(32).trimEnd() + "…"
}

private val URL_RX = Regex("https?://\\S+|www\\.\\S+")

/** True for emoji/symbol chars (incl. surrogate halves) that TTS reads aloud badly. */
private fun isSpeechNoise(c: Char): Boolean {
    val v = c.code
    return v in 0x2190..0x21FF || v in 0x2300..0x27BF || v in 0x2B00..0x2BFF ||
        v in 0xFE00..0xFEFF || v == 0x200D || v in 0xD800..0xDFFF
}

/** Strip things TTS reads aloud badly (emoji, markdown, URLs). Pure, tested. */
fun cleanForSpeech(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) sb.append(if (isSpeechNoise(c)) ' ' else c)
    var s = sb.toString()
    s = URL_RX.replace(s, " link ")
    s = s.replace(Regex("[*_`#>~|]+"), " ")
    s = s.replace("•", ", ").replace("→", ", ").replace("—", ", ").replace("–", ", ")
    s = s.replace("&", " and ").replace("%", " percent ").replace("=", " equals ")
    s = s.replace(Regex("\\s+"), " ").trim()
    return s
}

/** Split into speakable chunks at sentence ends (incl. Bengali/Devanagari danda). Pure, tested. */
fun splitSentences(text: String, maxLen: Int = 1500): List<String> {
    val parts = text.split(Regex("(?<=[.!?।\\n])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    val out = mutableListOf<String>()
    for (p in parts) {
        if (p.length <= maxLen) {
            out.add(p)
            continue
        }
        var rest = p
        while (rest.length > maxLen) {
            val cut = rest.lastIndexOf(' ', maxLen).takeIf { it > maxLen / 2 } ?: maxLen
            out.add(rest.take(cut).trim())
            rest = rest.drop(cut).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
    }
    return out.filter { it.isNotEmpty() }
}

/** True if a transcript contains the wake word. Pure, tested. */
fun hearsWakeWord(text: String): Boolean = text.contains("jarvis", ignoreCase = true)

object Models {
    // Hardcoded fallback only — Jarvis auto-discovers working models per key (ListModels).
    val FALLBACK = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-3-flash"
    )
}

// ---------- calculator (pure Kotlin, unit-tested) ----------

object Calculator {
    fun humanize(expr: String): String {
        var e = expr.trim().trimEnd('?').trim()
        e = Regex("""(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)""").replace(e, "($1/100*$2)")
        e = e.replace("^", "**")
        return e
    }

    fun evaluate(expr: String): Double {
        val v = Parser(humanize(expr)).parse()
        if (!v.isFinite()) throw ArithmeticException("result not finite")
        return v
    }

    fun format(v: Double): String =
        if (v % 1.0 == 0.0 && v < 1e15 && v > -1e15) v.toLong().toString() else v.toString()

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double {
            val v = additive()
            skip()
            if (i != s.length) throw IllegalArgumentException("unexpected '${s[i]}'")
            return v
        }
        fun additive(): Double {
            var v = multiplicative()
            while (true) {
                skip()
                v = when {
                    eat('+') -> v + multiplicative()
                    eat('-') -> v - multiplicative()
                    else -> return v
                }
            }
        }
        fun multiplicative(): Double {
            var v = power()
            while (true) {
                skip()
                v = when {
                    eat('*') -> v * power()
                    eat('/') -> v / power()
                    eat('%') -> v % power()
                    else -> return v
                }
            }
        }
        fun power(): Double {
            val v = unary()
            skip()
            if (i + 1 < s.length && s[i] == '*' && s[i + 1] == '*') {
                i += 2
                return Math.pow(v, power())
            }
            return v
        }
        fun unary(): Double {
            skip()
            return when {
                eat('+') -> unary()
                eat('-') -> -unary()
                else -> primary()
            }
        }
        fun primary(): Double {
            skip()
            if (eat('(')) {
                val v = additive()
                skip()
                if (!eat(')')) throw IllegalArgumentException("missing )")
                return v
            }
            if (i < s.length && s[i].isLetter()) {
                val name = readName()
                skip()
                if (!eat('(')) throw IllegalArgumentException("unknown '$name'")
                val args = mutableListOf<Double>()
                skip()
                if (!peek(')')) {
                    while (true) {
                        args += additive()
                        skip()
                        if (eat(',')) continue
                        break
                    }
                }
                if (!eat(')')) throw IllegalArgumentException("missing )")
                return when (name) {
                    "sqrt" -> kotlin.math.sqrt(args.single())
                    "abs" -> kotlin.math.abs(args.single())
                    "round" -> kotlin.math.round(args.single()).toDouble()
                    "min" -> args.minOrNull() ?: throw IllegalArgumentException("min needs args")
                    "max" -> args.maxOrNull() ?: throw IllegalArgumentException("max needs args")
                    "pow" -> {
                        if (args.size != 2) throw IllegalArgumentException("pow needs 2 args")
                        Math.pow(args[0], args[1])
                    }
                    else -> throw IllegalArgumentException("unknown function '$name'")
                }
            }
            return readNumber()
        }
        fun readNumber(): Double {
            skip()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("expected a number")
            return s.substring(start, i).toDouble()
        }
        fun readName(): String {
            val start = i
            while (i < s.length && s[i].isLetter()) i++
            return s.substring(start, i)
        }
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun eat(c: Char): Boolean {
            skip()
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }
        fun peek(c: Char): Boolean {
            skip()
            return i < s.length && s[i] == c
        }
    }
}

// ---------- offline tool router (pure Kotlin, unit-tested) ----------

object Router {
    data class Hit(val tool: String, val arg: String)

    private val mathy = Regex("""^[\d\s+\-*/().%^!]+$""")

    fun detect(raw: String): Hit? {
        val t = raw.trim()
        val low = t.lowercase()
        if (Regex("""\b(time|date|day is it|clock)\b""").containsMatchIn(low)) return Hit("time", "")
        Regex("""calc(?:ulate)?\s+(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return Hit("calc", it.groupValues[1].trim().trimEnd('?'))
        }
        Regex("""what is (.+?)\??$""").find(low)?.let {
            val e = it.groupValues[1].trim()
            if (mathy.matches(e) && e.any(Char::isDigit)) return Hit("calc", e)
        }
        Regex("""\bremember (?:that )?(.+)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return Hit("remember", it.groupValues[1].trim().trimEnd('?'))
        }
        if (low.startsWith("recall")) return Hit("recall", t.drop(6).trim())
        if ("what do you remember" in low || "my memor" in low) return Hit("recall", "")
        return null
    }
}

// ---------- on-device storage ----------

class Store(context: Context) {
    private val p = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v.trim()).apply()

    var model: String
        get() = p.getString("model", Models.FALLBACK[0]) ?: Models.FALLBACK[0]
        set(v) = p.edit().putString("model", v).apply()

    var ttsEnabled: Boolean
        get() = p.getBoolean("tts", true)
        set(v) = p.edit().putBoolean("tts", v).apply()

    var ttsVoice: String
        get() = p.getString("tts_voice", "") ?: ""
        set(v) = p.edit().putString("tts_voice", v).apply()

    fun facts(): MutableList<String> =
        p.getStringSet("facts", emptySet())?.toMutableList() ?: mutableListOf()

    fun addFact(f: String) {
        val all = facts()
        all.add(0, f)
        p.edit().putStringSet("facts", all.take(50).toSet()).apply()
    }

    fun removeFact(f: String) {
        val all = facts()
        all.remove(f)
        p.edit().putStringSet("facts", all.toSet()).apply()
    }

    fun clearFacts() {
        p.edit().remove("facts").apply()
    }

    fun searchFacts(q: String): List<String> {
        val all = facts()
        if (q.isBlank()) return all.take(10)
        return all.filter { it.contains(q, ignoreCase = true) }.take(10)
    }

    /** Legacy single history (pre-chats). Read once for migration, then deleted. */
    fun loadHistory(): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val arr = JSONArray(p.getString("history", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getString("r") to o.getString("t"))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun removeLegacyHistory() {
        p.edit().remove("history").apply()
    }

    fun loadChats(): MutableList<ChatData> {
        val out = mutableListOf<ChatData>()
        try {
            val arr = JSONArray(p.getString("chats_v1", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val marr = o.optJSONArray("msgs") ?: JSONArray()
                val msgs = mutableListOf<Pair<String, String>>()
                for (j in 0 until marr.length()) {
                    val m = marr.getJSONObject(j)
                    msgs.add(m.getString("r") to m.getString("t"))
                }
                out.add(ChatData(o.getString("id"), o.optString("title", "Chat"), msgs))
            }
        } catch (_: Exception) {
        }
        return out.take(30).toMutableList()
    }

    fun saveChats(chats: List<ChatData>) {
        try {
            val arr = JSONArray()
            for (c in chats.take(30)) {
                val marr = JSONArray()
                for ((r, t) in c.msgs.takeLast(40)) {
                    marr.put(JSONObject().put("r", r).put("t", t.take(2000)))
                }
                arr.put(JSONObject().put("id", c.id).put("title", c.title.take(60)).put("msgs", marr))
            }
            p.edit().putString("chats_v1", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadActiveId(): String = p.getString("active_chat", "") ?: ""
    fun saveActiveId(id: String) = p.edit().putString("active_chat", id).apply()

    fun cachedModels(): List<String> {
        return try {
            val arr = JSONArray(p.getString("models_cache", "[]") ?: "[]")
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun modelsCachedAt(): Long = p.getLong("models_cached_at", 0)

    fun saveModels(models: List<String>) {
        val arr = JSONArray()
        models.forEach { arr.put(it) }
        p.edit().putString("models_cache", arr.toString())
            .putLong("models_cached_at", System.currentTimeMillis()).apply()
    }
}

// ---------- Gemini REST API (direct, no SDK) ----------

object GeminiApi {
    class JarvisError(msg: String) : Exception(msg)

    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Names (no "models/" prefix) of models supporting generateContent. Pure, tested. */
    fun parseModelNames(json: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val arr = JSONObject(json).optJSONArray("models") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val methods = o.optJSONArray("supportedGenerationMethods") ?: continue
                var ok = false
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") { ok = true; break }
                }
                if (ok) {
                    val name = o.optString("name").removePrefix("models/")
                    // TTS/embedding models also list generateContent but can't chat — exclude.
                    if (!name.contains("tts", ignoreCase = true) &&
                        !name.contains("embed", ignoreCase = true)
                    ) {
                        out.add(name)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out.filter { it.isNotBlank() }
    }

    /** Preferred-first, then flash-lite, flash, others. Pure, tested. */
    fun pickModels(preferred: String, available: List<String>): List<String> {
        // Drop TTS/embedding models (also heals caches saved before parse filtering).
        val pool = available.filterNot {
            it.contains("tts", ignoreCase = true) || it.contains("embed", ignoreCase = true)
        }.ifEmpty { available }
        if (pool.isEmpty()) return listOf(preferred).filter { it.isNotBlank() }
        val rest = pool.filter { it != preferred }.sortedWith(
            compareBy(
                { n: String ->
                    when {
                        "flash-lite" in n -> 0
                        "flash" in n -> 1
                        else -> 2
                    }
                },
                { it }
            )
        )
        val head = if (preferred.isNotBlank() && preferred in pool) listOf(preferred) else emptyList()
        return (head + rest).ifEmpty { pool }
    }

    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code in 400..403 && ("API key" in txt || "API_KEY" in txt)) {
                    throw JarvisError("API key rejected. Open Settings (⚙️) and check the key.")
                }
                throw JarvisError("Couldn't list models (HTTP ${resp.code}).")
            }
            parseModelNames(txt)
        }
    }

    /** Tries each model in order. Returns (reply, modelUsed). Errors aggregated. */
    suspend fun chat(
        apiKey: String,
        models: List<String>,
        system: String,
        history: List<Pair<String, String>>,
        user: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val usable = history.dropWhile { it.first != "user" }
        val contents = JSONArray()
        for ((r, t) in usable.takeLast(20)) {
            contents.put(
                JSONObject().put("role", r)
                    .put("parts", JSONArray().put(JSONObject().put("text", t)))
            )
        }
        contents.put(
            JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))
        )
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
            .toString()

        val errs = mutableListOf<String>()
        for (m in models) {
            val res = try {
                callOnce(m, apiKey, body)
            } catch (e: Exception) {
                Triple(false, "", "Network error: ${e.message?.take(100)}")
            }
            if (res.first) return@withContext res.second to m
            if (res.third.startsWith("KEY:")) throw JarvisError(res.third.removePrefix("KEY:"))
            errs.add("$m → ${res.third.take(150)}")
        }
        throw JarvisError("All ${models.size} models failed:\n" + errs.joinToString("\n") { "• $it" })
    }

    private fun callOnce(model: String, apiKey: String, body: String): Triple<Boolean, String, String> {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 400 && ("API key" in txt || "API_KEY" in txt)) {
                    return Triple(false, "", "KEY:API key rejected. Open Settings (⚙️) and check the key.")
                }
                return Triple(false, "", "HTTP ${resp.code}: ${txt.take(160)}")
            }
            val text = try {
                JSONObject(txt).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            } catch (_: Exception) {
                null
            }
            if (text.isNullOrEmpty()) return Triple(false, "", "Empty reply (possibly blocked). Try rephrasing.")
            return Triple(true, text, "")
        }
    }
}

// ---------- ViewModel: chat state + tools + brain + voice ----------

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val audio: AudioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val messages = mutableStateListOf<ChatMessage>()
    val availableModels = mutableStateListOf<String>()
    val chats = mutableStateListOf<ChatData>()
    val ttsVoices = mutableStateListOf<TtsVoice>()
    var busy by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
    var showChats by mutableStateOf(false)
    var showMemory by mutableStateOf(false)
    var settingsMsg by mutableStateOf("")
        private set
    var apiKey by mutableStateOf(store.apiKey)
        private set
    var model by mutableStateOf(store.model)
        private set
    var activeChatId by mutableStateOf("")
        private set
    var memTick by mutableStateOf(0)
        private set
    var ttsOn by mutableStateOf(store.ttsEnabled)
        private set
    var voiceName by mutableStateOf(store.ttsVoice)
        private set
    var listening by mutableStateOf(false)
        private set
    var wakeOn by mutableStateOf(WakeService.isRunning)
        private set

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var listenTries = 0
    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).build()
    }

    // Owner key, baked at build time from the GEMINI_API_KEY repo secret
    // (stored reversed+Base64 so it isn't plainly greppable inside the APK).
    // It is completely invisible in the UI: no screen mentions it.
    // NOTE: obfuscation, not encryption — anyone decompiling the APK can recover it.
    // Real protection = restrict the key in Google Cloud + keep the APK private.
    private val builtinKey: String = try {
        val obf = BuildConfig.DEFAULT_GEMINI_KEY
        if (obf.isBlank()) "" else String(
            android.util.Base64.decode(obf, android.util.Base64.DEFAULT)
        ).reversed()
    } catch (_: Exception) {
        ""
    }

    /** User's own key if pasted, else the invisible built-in key. */
    private val effectiveKey: String get() = apiKey.ifBlank { builtinKey }

    val brainOk: Boolean get() = effectiveKey.isNotBlank()

    init {
        val cached = store.cachedModels()
        availableModels.addAll(cached.ifEmpty { Models.FALLBACK })
        val loaded = store.loadChats()
        if (loaded.isEmpty()) {
            val legacy = store.loadHistory()
            if (legacy.isNotEmpty()) {
                loaded.add(ChatData("c1", chatTitle(legacy), legacy.toMutableList()))
            } else {
                loaded.add(ChatData("c1", "New chat", mutableListOf()))
            }
            store.removeLegacyHistory()
        }
        chats.addAll(loaded)
        val savedId = store.loadActiveId()
        activeChatId = if (loaded.any { it.id == savedId }) savedId else loaded[0].id
        val active = loaded.first { it.id == activeChatId }
        for ((r, t) in active.msgs) {
            messages.add(ChatMessage(if (r == "user") "user" else "bot", t))
        }
        if (messages.isEmpty()) {
            messages.add(ChatMessage("bot", greet()))
        }
        createTts("com.google.android.tts")
    }

    override fun onCleared() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        commandAudioEnd()
        destroyRecognizer()
        super.onCleared()
    }

    private fun greet(): String =
        if (brainOk) "Hello. I am Jarvis. How can I help?"
        else "Hello. I am Jarvis.\n\n🔑 Add a Gemini key in Settings (⚙️, top right) to wake my brain — free from aistudio.google.com. Meanwhile I can still tell time, calculate, and remember things — try 'what time is it?'"

    // ---- voice output (Jarvis-style male voice, human prosody) ----

    private fun createTts(engine: String?) {
        try {
            tts = TextToSpeech(getApplication(), { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyVoice()
                    loadVoices()
                } else if (engine != null) {
                    // Preferred engine failed — fall back to the default engine.
                    createTts(null)
                }
            }, engine)
        } catch (_: Exception) {
            if (engine != null) createTts(null)
        }
    }

    private fun applyVoice() {
        val t = tts ?: return
        try {
            val saved = store.ttsVoice
            val match = t.voices?.firstOrNull { it.name == saved } ?: pickJarvisVoice(t)
            if (match != null) {
                t.voice = match
                voiceName = match.name
                store.ttsVoice = match.name // persist auto-pick so the service uses it too
            } else {
                t.language = Locale.getDefault()
                voiceName = ""
            }
            t.setSpeechRate(0.95f)
            t.setPitch(0.9f)
        } catch (_: Exception) {
        }
    }

    private fun pickJarvisVoice(t: TextToSpeech): android.speech.tts.Voice? {
        val all = try {
            t.voices
        } catch (_: Exception) {
            null
        } ?: return null
        val lang = Locale.getDefault().language
        fun isMale(v: android.speech.tts.Voice): Boolean =
            v.name.contains("male", ignoreCase = true) &&
                !v.name.contains("female", ignoreCase = true)
        return all.firstOrNull { isMale(it) && it.locale?.language == lang }
            ?: all.firstOrNull { isMale(it) }
            ?: all.firstOrNull { it.locale?.language == lang }
    }

    private fun loadVoices() {
        val t = tts ?: return
        try {
            val all = t.voices ?: return
            val lang = Locale.getDefault().language
            val scored = all.map { v ->
                val male = v.name.contains("male", ignoreCase = true) &&
                    !v.name.contains("female", ignoreCase = true)
                val score = when {
                    male && v.locale?.language == lang -> 0
                    male -> 1
                    v.locale?.language == lang -> 2
                    else -> 3
                }
                v to score
            }.sortedWith(compareBy({ it.second }, { it.first.name }))
            ttsVoices.clear()
            ttsVoices.addAll(scored.take(14).map { (v, _) ->
                val isMale = v.name.contains("male", ignoreCase = true) &&
                    !v.name.contains("female", ignoreCase = true)
                TtsVoice(
                    v.name,
                    "${v.locale?.displayLanguage ?: "?"} • " +
                        (if (isMale) "Male" else "Voice") +
                        (if (v.isNetworkConnectionRequired) " • online" else "")
                )
            })
        } catch (_: Exception) {
        }
    }

    fun selectVoice(id: String) {
        store.ttsVoice = id
        voiceName = id
        applyVoice()
        previewVoice()
    }

    fun previewVoice() {
        speak("Hello. I am Jarvis, at your service.", force = true)
    }

    fun toggleTts() {
        ttsOn = !ttsOn
        store.ttsEnabled = ttsOn
        if (!ttsOn) stopSpeaking()
    }

    private fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun speak(text: String, force: Boolean = false) {
        if (!ttsOn && !force) return
        val t = tts ?: return
        try {
            val clean = cleanForSpeech(text)
            if (clean.isEmpty()) return
            val chunks = splitSentences(clean)
            if (chunks.isEmpty()) return
            t.speak(chunks[0], TextToSpeech.QUEUE_FLUSH, null, "jarvis")
            for (c in chunks.drop(1)) t.speak(c, TextToSpeech.QUEUE_ADD, null, "jarvis")
        } catch (_: Exception) {
        }
    }

    // ---- voice input (in-app, no Google popup, no beeps) ----

    fun startListening() {
        try {
            destroyRecognizer()
            pauseWakeService()
            val ctx = getApplication<Application>()
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                toast("Voice input not available on this device")
                commandAudioEnd()
                resumeWakeService()
                return
            }
            commandAudioBegin()
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    bubbleRed()
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    destroyRecognizer()
                    listenTries = 0
                    commandAudioEnd()
                    bubbleBlue()
                    if (heard.isNotEmpty()) send(heard)
                    resumeWakeService()
                }

                override fun onError(error: Int) {
                    listening = false
                    destroyRecognizer()
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && ++listenTries <= 3) {
                        // Mic still held (e.g. by the wake loop shutting down) — retry.
                        viewModelScope.launch {
                            delay(600)
                            startListening()
                        }
                        return
                    }
                    listenTries = 0
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        toast("Didn't catch that — try again")
                    } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                        toast("Voice error ($error)")
                    }
                    commandAudioEnd()
                    bubbleBlue()
                    resumeWakeService()
                }

                override fun onEndOfSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            r.startListening(intent)
        } catch (_: Exception) {
            listening = false
            destroyRecognizer()
            listenTries = 0
            commandAudioEnd()
            bubbleBlue()
            resumeWakeService()
        }
    }

    fun startListeningDelayed(ms: Long) {
        viewModelScope.launch {
            delay(ms)
            if (!listening) startListening()
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        listening = false
        commandAudioEnd()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    /** Duck everything + mute recognition beeps for a voice-command session. */
    private fun commandAudioBegin() {
        try {
            audio.requestAudioFocus(focusRequest)
        } catch (_: Exception) {
        }
        muteBeeps(true)
    }

    private fun commandAudioEnd() {
        muteBeeps(false)
        try {
            audio.abandonAudioFocusRequest(focusRequest)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun muteBeeps(mute: Boolean) {
        try {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, mute)
            audio.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) {
        try {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    // ---- wake word service control ----

    fun setWakeEnabled(on: Boolean) {
        val appCtx = getApplication<Application>()
        if (on) {
            stopListening()
            try {
                appCtx.startForegroundService(
                    Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_START)
                )
            } catch (e: Exception) {
                toast("Couldn't start wake service")
                return
            }
            wakeOn = true
        } else {
            try {
                appCtx.stopService(Intent(appCtx, WakeService::class.java))
            } catch (_: Exception) {
            }
            wakeOn = false
        }
    }

    fun syncWakeState() {
        wakeOn = WakeService.isRunning
    }

    private fun pauseWakeService() {
        try {
            val appCtx = getApplication<Application>()
            appCtx.startService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_PAUSE))
        } catch (_: Exception) {
        }
    }

    private fun resumeWakeService() {
        try {
            val appCtx = getApplication<Application>()
            appCtx.startService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_RESUME))
        } catch (_: Exception) {
        }
    }

    private fun bubbleRed() {
        try {
            val appCtx = getApplication<Application>()
            appCtx.startService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_BUBBLE_RED))
        } catch (_: Exception) {
        }
    }

    private fun bubbleBlue() {
        try {
            val appCtx = getApplication<Application>()
            appCtx.startService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_BUBBLE_BLUE))
        } catch (_: Exception) {
        }
    }

    // ---- multi-chat ----

    fun newChat() {
        stopSpeaking()
        stopListening()
        persist()
        val c = ChatData("c" + System.currentTimeMillis(), "New chat", mutableListOf())
        chats.add(0, c)
        activeChatId = c.id
        messages.clear()
        messages.add(
            ChatMessage(
                "bot",
                if (brainOk) "New chat started. What's on your mind?"
                else "New chat started. Add a key in ⚙️ to wake my brain."
            )
        )
        showChats = false
        persist()
    }

    fun switchChat(id: String) {
        stopSpeaking()
        stopListening()
        if (id == activeChatId) {
            showChats = false
            return
        }
        persist()
        val c = chats.firstOrNull { it.id == id } ?: return
        activeChatId = id
        messages.clear()
        for ((r, t) in c.msgs) {
            messages.add(ChatMessage(if (r == "user") "user" else "bot", t))
        }
        if (messages.isEmpty()) messages.add(ChatMessage("bot", greet()))
        showChats = false
        persist()
    }

    fun deleteChat(id: String) {
        persist()
        chats.removeAll { it.id == id }
        if (chats.isEmpty()) {
            chats.add(ChatData("c" + System.currentTimeMillis(), "New chat", mutableListOf()))
        }
        if (activeChatId == id) {
            stopSpeaking()
            activeChatId = chats[0].id
            messages.clear()
            for ((r, t) in chats[0].msgs) {
                messages.add(ChatMessage(if (r == "user") "user" else "bot", t))
            }
            if (messages.isEmpty()) messages.add(ChatMessage("bot", greet()))
        }
        persist()
    }

    // ---- memories ----

    fun memories(): List<String> = store.facts()

    fun addMemory(s: String) {
        val t = s.trim()
        if (t.isEmpty()) return
        store.addFact(t)
        memTick++
    }

    fun removeMemory(s: String) {
        store.removeFact(s)
        memTick++
    }

    fun clearMemories() {
        store.clearFacts()
        memTick++
    }

    // ---- settings ----

    fun openSettings() {
        settingsMsg = ""
        showSettings = true
        loadVoices()
    }

    fun saveSettings(key: String, model: String) {
        val oldEff = effectiveKey
        store.apiKey = key
        store.model = model
        apiKey = store.apiKey
        this.model = store.model
        showSettings = false
        settingsMsg = ""
        if (oldEff != effectiveKey) {
            // Different key → different model access. Re-discover.
            store.saveModels(emptyList())
            availableModels.clear()
            availableModels.addAll(Models.FALLBACK)
            if (brainOk) refreshModels()
        }
        if (brainOk && messages.size == 1) {
            messages.add(ChatMessage("bot", "Brain connected. 🟢 What shall we do first?"))
            persist()
        }
    }

    fun refreshModels(keyOverride: String = "") {
        val k = keyOverride.ifBlank { effectiveKey }
        if (k.isBlank()) {
            settingsMsg = "Add an API key in Settings first."
            return
        }
        settingsMsg = "Checking available models…"
        viewModelScope.launch {
            try {
                val available = GeminiApi.listModels(k)
                store.saveModels(available)
                availableModels.clear()
                availableModels.addAll(available.ifEmpty { Models.FALLBACK })
                settingsMsg = if (available.isEmpty()) {
                    "Key works, but no chat models found for it."
                } else {
                    "${available.size} models available — lowest-cost first."
                }
            } catch (e: Exception) {
                settingsMsg = "Failed: ${e.message?.take(140)}"
            }
        }
    }

    private suspend fun resolveModels(force: Boolean): List<String> {
        val fresh = System.currentTimeMillis() - store.modelsCachedAt() < 24 * 3600 * 1000L
        if (!force) {
            val cached = store.cachedModels()
            if (cached.isNotEmpty() && fresh) return GeminiApi.pickModels(model, cached)
        }
        return try {
            val available = GeminiApi.listModels(effectiveKey)
            store.saveModels(available)
            availableModels.clear()
            availableModels.addAll(available.ifEmpty { Models.FALLBACK })
            GeminiApi.pickModels(model, available.ifEmpty { Models.FALLBACK })
        } catch (_: Exception) {
            val cached = store.cachedModels()
            GeminiApi.pickModels(model, cached.ifEmpty { Models.FALLBACK })
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        messages.add(ChatMessage("user", text))
        persist()
        Router.detect(text)?.let { hit ->
            val reply = runTool(hit)
            messages.add(ChatMessage("bot", reply))
            speak(reply)
            persist()
            return
        }
        if (!brainOk) {
            val reply = "🔑 I need a Gemini API key for that (free from aistudio.google.com — add it in Settings ⚙️). Offline I can still do time, calculations, and memory."
            messages.add(ChatMessage("bot", reply))
            speak(reply)
            persist()
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val system = buildSystem(store.facts())
                val hist = messages.dropLast(1)
                    .map { (if (it.role == "user") "user" else "model") to it.text }
                val (reply, _) = try {
                    GeminiApi.chat(effectiveKey, resolveModels(false), system, hist, text)
                } catch (e: GeminiApi.JarvisError) {
                    if (!e.message.orEmpty().contains("404")) throw e
                    // Model list went stale — rediscover once and retry.
                    GeminiApi.chat(effectiveKey, resolveModels(true), system, hist, text)
                }
                messages.add(ChatMessage("bot", reply))
                speak(reply)
            } catch (e: Exception) {
                messages.add(ChatMessage("bot", "⚠️ ${e.message}"))
            } finally {
                busy = false
                persist()
            }
        }
    }

    private fun runTool(hit: Router.Hit): String = when (hit.tool) {
        "time" -> {
            val now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a"))
            "It's $now."
        }
        "calc" -> try {
            "= ${Calculator.format(Calculator.evaluate(hit.arg))}"
        } catch (_: Exception) {
            "Couldn't calculate that. Try something like 2+2*3 or sqrt(16)."
        }
        "remember" -> {
            if (hit.arg.isBlank()) "Tell me what to remember."
            else {
                store.addFact(hit.arg)
                memTick++
                "Noted! I'll remember that."
            }
        }
        "recall" -> {
            val found = store.searchFacts(hit.arg)
            if (found.isEmpty()) "I don't have any saved memories yet."
            else "Here's what I remember:\n" + found.joinToString("\n") { "• $it" }
        }
        else -> "?"
    }

    private fun buildSystem(facts: List<String>): String {
        val base = "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. " +
            "Be warm, a little witty, and helpful. Keep answers short enough for a phone screen unless asked for detail."
        if (facts.isEmpty()) return base
        return base + "\nThings you remember about your owner:\n" + facts.take(10).joinToString("\n") { "- $it" }
    }

    private fun persist() {
        val c = chats.firstOrNull { it.id == activeChatId } ?: return
        c.msgs.clear()
        c.msgs.addAll(messages.map { (if (it.role == "user") "user" else "model") to it.text })
        c.title = chatTitle(c.msgs)
        store.saveChats(chats)
        store.saveActiveId(activeChatId)
    }
}

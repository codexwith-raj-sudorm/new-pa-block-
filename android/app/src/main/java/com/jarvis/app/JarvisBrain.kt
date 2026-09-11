package com.jarvis.app

import android.Manifest
import android.app.AlarmManager
import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.ContactsContract
import android.provider.Settings
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.room.Room
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
import java.time.ZoneId
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
    val src = CODE_FENCE_RX.replace(text, " code snippet ")
    val sb = StringBuilder(src.length)
    for (c in src) sb.append(if (isSpeechNoise(c)) ' ' else c)
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

/** One segment of a chat message: plain prose or a fenced code block. Pure. */
data class CodeSeg(val isCode: Boolean, val lang: String, val text: String)

private val CODE_FENCE_RX = Regex("```(\\w*)\\n?([\\s\\S]*?)```")

/** Split ```fenced``` code blocks out of chat text (Protocol Gamma). Pure, tested. */
fun splitCodeBlocks(text: String): List<CodeSeg> {
    val out = mutableListOf<CodeSeg>()
    var last = 0
    for (m in CODE_FENCE_RX.findAll(text)) {
        if (m.range.first > last) {
            val prose = text.substring(last, m.range.first)
            if (prose.isNotEmpty()) out.add(CodeSeg(false, "", prose))
        }
        out.add(CodeSeg(true, m.groupValues[1].ifBlank { "code" }, m.groupValues[2].trimEnd()))
        last = m.range.last + 1
    }
    if (last < text.length) out.add(CodeSeg(false, "", text.substring(last)))
    return out.filter { it.text.isNotEmpty() }
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

/** Build a shareable plain-text transcript of a chat (pure, tested). */
fun chatTranscript(title: String, msgs: List<ChatMessage>): String {
    val sb = StringBuilder("JARVIS - ")
    sb.append(title.ifBlank { "Chat" }).append("\n\n")
    for (m in msgs) {
        sb.append(if (m.role == "user") "You: " else "Jarvis: ")
        sb.append(m.text.trim()).append("\n\n")
    }
    return sb.toString().trimEnd() + "\n"
}

/** Build the prompt for a Share Hub quick action (pure, tested). */
fun sharePrompt(kind: String, text: String): String {
    val t = text.trim().take(4000)
    return when (kind) {
        "sum" -> "Summarize this in 3 short bullets:\n$t"
        "eli5" -> "Explain this like I'm 5 years old:\n$t"
        "bugs" -> "Review this code for bugs, then show the fixed code in a fenced block:\n$t"
        else -> "Translate this to Hindi (give Roman + Devanagari):\n$t"
    }
}

/** Device telemetry snapshot for the Briefing card. */
data class Briefing(
    val batteryPct: Int,
    val charging: Boolean,
    val memUsedMb: Long,
    val memTotalMb: Long,
    val storeFreeGb: Double,
    val storeTotalGb: Double,
    val netName: String
)

/** Format a Briefing into (label, value) rows (pure, tested). */
fun formatBriefing(b: Briefing): List<Pair<String, String>> = listOf(
    "Battery" to "${b.batteryPct}%${if (b.charging) " (charging)" else ""}",
    "Memory" to "${b.memUsedMb} / ${b.memTotalMb} MB",
    "Storage free" to "${"%.1f".format(b.storeFreeGb)} / ${"%.0f".format(b.storeTotalGb)} GB",
    "Network" to b.netName
)

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
        Regex("""\b(cancel|delete|remove) reminder (\d+)""").find(low)?.let {
            return Hit("reminder_cancel", it.groupValues[2])
        }
        if (low == "reminders" || "my reminder" in low || low.startsWith("list reminders")) return Hit("reminders", "")
        if (low.startsWith("remind me")) return Hit("remind", t)
        parseDeviceCommand(t)?.let { return Hit("device", t) }
        parseListCommand(t)?.let { return Hit("lists", t) }
        return null
    }
}

// ---------- on-device storage ----------

class Store(context: Context) {
    private val appCtx = context.applicationContext
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

    var wakeEnabled: Boolean
        get() = p.getBoolean("wake", false)
        set(v) = p.edit().putBoolean("wake", v).apply()

    var continuous: Boolean
        get() = p.getBoolean("continuous", false)
        set(v) = p.edit().putBoolean("continuous", v).apply()

    var batteryAsked: Boolean
        get() = p.getBoolean("battery_asked", false)
        set(v) = p.edit().putBoolean("battery_asked", v).apply()

    var lastSeenCode: Int
        get() = p.getInt("last_seen_code", 0)
        set(v) = p.edit().putInt("last_seen_code", v).apply()

    // Room vault (stark_vault): migrates legacy prefs once, purges 30-day TTL
    // on first touch each process. Main-thread queries are OK here — the
    // table is capped at 200 tiny rows, so reads stay sub-millisecond.
    private val vault: VaultDao by lazy {
        val dao = Room.databaseBuilder(appCtx, VaultDb::class.java, "stark_vault")
            .allowMainThreadQueries()
            .build().dao()
        if (!p.getBoolean("vault_migrated", false)) {
            val legacy = p.getStringSet("facts", emptySet()).orEmpty()
            for (f in migrateLegacyFacts(legacy, System.currentTimeMillis())) dao.insert(f)
            p.edit().putBoolean("vault_migrated", true).remove("facts").apply()
        }
        dao.purgeBefore(vaultCutoff(System.currentTimeMillis()))
        dao
    }

    fun facts(): MutableList<String> = try {
        vault.all().map { it.text }.toMutableList()
    } catch (_: Exception) { mutableListOf() }

    fun addFact(f: String) {
        val t = f.trim().take(500)
        if (t.isEmpty()) return
        try {
            vault.insert(VaultFact(text = t, ts = System.currentTimeMillis()))
            vault.trimTo(200)
        } catch (_: Exception) {
        }
    }

    fun removeFact(f: String) {
        try { vault.deleteText(f) } catch (_: Exception) { }
    }

    fun clearFacts() {
        try { vault.clear() } catch (_: Exception) { }
    }

    fun searchFacts(q: String): List<String> {
        val all = facts()
        if (q.isBlank()) return all.take(10)
        return all.filter { it.contains(q, ignoreCase = true) }.take(10)
    }

    fun loadReminders(): MutableList<ReminderItem> {
        val out = mutableListOf<ReminderItem>()
        try {
            val arr = JSONArray(p.getString("reminders_v1", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ReminderItem(o.optInt("id", i), o.optLong("at", 0), o.optString("text", "")))
            }
        } catch (_: Exception) { }
        return out
    }

    fun saveReminders(list: List<ReminderItem>) {
        try {
            val arr = JSONArray()
            for (r in list) arr.put(JSONObject().put("id", r.id).put("at", r.at).put("text", r.text))
            p.edit().putString("reminders_v1", arr.toString()).apply()
        } catch (_: Exception) { }
    }

    fun removeReminder(id: Int) {
        saveReminders(loadReminders().filterNot { it.id == id })
    }

    fun nextReminderId(): Int {
        val n = p.getInt("reminder_seq", 1)
        p.edit().putInt("reminder_seq", n + 1).apply()
        return n
    }

    fun loadTodos(): MutableList<TodoItem> {
        val out = mutableListOf<TodoItem>()
        try {
            val arr = JSONArray(p.getString("todos_v1", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(TodoItem(o.optString("text", ""), o.optBoolean("done", false)))
            }
        } catch (_: Exception) { }
        return out.filterTo(mutableListOf()) { it.text.isNotBlank() }
    }

    fun saveTodos(list: List<TodoItem>) {
        try {
            val arr = JSONArray()
            for (x in list.take(100)) arr.put(JSONObject().put("text", x.text).put("done", x.done))
            p.edit().putString("todos_v1", arr.toString()).apply()
        } catch (_: Exception) { }
    }

    fun loadNotes(): MutableList<String> =
        p.getStringSet("notes_v1", emptySet())?.toMutableList() ?: mutableListOf()

    fun addNote(n: String) {
        val all = loadNotes()
        all.add(0, n)
        p.edit().putStringSet("notes_v1", all.take(100).toSet()).apply()
    }

    fun removeNote(n: String) {
        val all = loadNotes()
        all.remove(n)
        p.edit().putStringSet("notes_v1", all.toSet()).apply()
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
    var showList by mutableStateOf(false)
    var showWhatsNew by mutableStateOf(false)
    var showShare by mutableStateOf(false)
    var showBriefing by mutableStateOf(false)
    var shareText by mutableStateOf("")
    var whatsNewFresh by mutableStateOf(false)
    var whatsNewItems by mutableStateOf<List<ChangelogEntry>>(emptyList())
    var settingsMsg by mutableStateOf("")
        private set
    var apiKey by mutableStateOf(store.apiKey)
        private set
    var model by mutableStateOf(store.model)
        private set
    var activeChatId by mutableStateOf("")
        private set
    var memTick by mutableStateOf(0)
    var listTick by mutableStateOf(0)
        private set
    var ttsOn by mutableStateOf(store.ttsEnabled)
    var continuous by mutableStateOf(store.continuous)
        private set
    var voiceName by mutableStateOf(store.ttsVoice)
        private set
    var listening by mutableStateOf(false)
        private set
    var permRequest by mutableStateOf<String?>(null)
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
            val key = personaKeyOrDefault(store.ttsVoice)
            val persona = personaForKey(key)
            val match = resolveEngineVoice(t, key)
            if (match != null) t.voice = match
            else t.language = Locale.getDefault()
            voiceName = key
            store.ttsVoice = key // persona key now (legacy engine names auto-heal to jarvis)
            t.setSpeechRate(persona.rate)
            t.setPitch(persona.pitch)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { SpeechState.speaking = true; HudStateBus.update(speaking = true) }
                override fun onDone(id: String?) { SpeechState.speaking = false; HudStateBus.update(speaking = false); if (continuous && ttsOn && !showSettings) Handler(Looper.getMainLooper()).post { try { startListening() } catch (_: Exception) {} } }
                override fun onError(id: String?) { SpeechState.speaking = false; HudStateBus.update(speaking = false) }
            })
        } catch (_: Exception) {
        }
    }

    private fun resolveEngineVoice(t: TextToSpeech, personaKey: String): android.speech.tts.Voice? {
        val all = try { t.voices } catch (_: Exception) { null }.orEmpty()
        if (all.isEmpty()) return null
        val infos = all.map {
            EngineVoiceInfo(it.name, it.locale?.language ?: "", it.locale?.country ?: "", it.isNetworkConnectionRequired, it.features?.toSet().orEmpty())
        }
        val loc = Locale.getDefault()
        val want = resolvePersonaVoices(infos, loc.language, loc.country ?: "")[personaKey] ?: return null
        return all.firstOrNull { it.name == want.name }
    }

    private fun loadVoices() {
        try {
            ttsVoices.clear()
            ttsVoices.addAll(VOICE_PERSONAS.map { p -> TtsVoice(p.key, "${p.name} — ${p.tagline}") })
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
        val name = personaForKey(voiceName).name
        speak("Hello. I am $name, at your service.", force = true)
    }

    fun toggleTts() {
        ttsOn = !ttsOn
        store.ttsEnabled = ttsOn
        if (!ttsOn) stopSpeaking()
    }

    fun toggleContinuous() {
        continuous = !continuous
        store.continuous = continuous
    }

    fun incomingShare(t: String) {
        shareText = t.trim().take(4000)
        showShare = shareText.isNotBlank()
    }

    fun shareAction(kind: String) {
        val t = shareText
        showShare = false
        shareText = ""
        if (t.isBlank()) return
        send(sharePrompt(kind, t))
    }

    fun exportChat() {
        try {
            val c = chats.firstOrNull { it.id == activeChatId } ?: return
            val t = chatTranscript(c.title, messages.toList())
            val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, t)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(Intent.createChooser(i, "Share chat"))
        } catch (_: Exception) {
        }
    }

    fun retryLast() {
        if (busy || messages.size < 2) return
        val last = messages.last()
        if (last.role != "bot" || !last.text.startsWith("⚠")) return
        val prev = messages[messages.size - 2]
        if (prev.role != "user") return
        messages.removeAt(messages.size - 1)
        messages.removeAt(messages.size - 1)
        send(prev.text)
    }

    private fun stopSpeaking() {
        SpeechState.speaking = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun interruptSpeech() {
        stopSpeaking()
        SpeechState.speaking = false
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
                    HudStateBus.update(listening = true)
                    HudStateBus.postTicker("[MIC: LIVE]")
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    destroyRecognizer()
                    listenTries = 0
                    commandAudioEnd()
                    if (heard.isNotEmpty()) send(heard)
                    resumeWakeService()
                }

                override fun onError(error: Int) {
                    listening = false
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
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
                    resumeWakeService()
                }

                override fun onEndOfSpeech() { BubbleLevelBus.reset() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { BubbleLevelBus.pushRms(rmsdB) }
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
            HudStateBus.update(listening = false)
            destroyRecognizer()
            listenTries = 0
            commandAudioEnd()
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
        HudStateBus.update(listening = false)
        BubbleLevelBus.reset()
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

    /** Mute ALL non-critical streams for the session (Google's beep routes per-OEM). */
    private fun muteBeeps(mute: Boolean) {
        if (mute) SoundMuter.mute(audio) else SoundMuter.unmute(audio)
    }

    private fun toast(msg: String) {
        try {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    // ---- wake word service control ----

    fun collectBriefing(): Briefing {
        val ctx = getApplication<Application>()
        var pct = -1
        var charging = false
        try {
            val batt = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scl = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (lvl >= 0 && scl > 0) pct = (lvl * 100 / scl)
            val st = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                st == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
        }
        var memUsed = -1L
        var memTotal = -1L
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            memTotal = mi.totalMem / (1024 * 1024)
            memUsed = (mi.totalMem - mi.availMem) / (1024 * 1024)
        } catch (_: Exception) {
        }
        var freeGb = -1.0
        var totalGb = -1.0
        try {
            freeGb = ctx.filesDir.usableSpace / 1e9
            totalGb = ctx.filesDir.totalSpace / 1e9
        } catch (_: Exception) {
        }
        var net = "offline"
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            net = when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "connected"
            }
        } catch (_: Exception) {
        }
        return Briefing(pct, charging, memUsed, memTotal, freeGb, totalGb, net)
    }

    fun batteryUnrestricted(): Boolean {
        return try {
            val ctx = getApplication<Application>()
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (_: Exception) { true }
    }

    fun requestBatteryUnrestricted() {
        try {
            val ctx = getApplication<Application>()
            val i = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {
            toast("Allow Jarvis to run unrestricted in battery settings.")
        }
    }

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
            store.wakeEnabled = true
            if (!store.batteryAsked) {
                store.batteryAsked = true
                if (!batteryUnrestricted()) requestBatteryUnrestricted()
            }
        } else {
            try {
                appCtx.stopService(Intent(appCtx, WakeService::class.java))
            } catch (_: Exception) {
            }
            wakeOn = false
            store.wakeEnabled = false
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

    fun checkWhatsNew() {
        try {
            val cur = BuildConfig.VERSION_CODE
            val last = store.lastSeenCode
            if (cur > last) {
                whatsNewFresh = last == 0
                whatsNewItems = if (last == 0) CHANGELOG.filter { it.code == cur } else whatsNew(last)
                if (whatsNewItems.isEmpty()) whatsNewItems = CHANGELOG.take(1)
                showWhatsNew = true
                store.lastSeenCode = cur
            }
        } catch (_: Exception) { }
    }

    fun todoItems(): List<TodoItem> = store.loadTodos()
    fun noteItems(): List<String> = store.loadNotes()

    fun addTodo(s: String) {
        val t = s.trim()
        if (t.isEmpty()) return
        val all = store.loadTodos()
        all.add(TodoItem(t, false))
        store.saveTodos(all)
        listTick++
    }

    fun toggleTodo(i: Int) {
        val all = store.loadTodos()
        if (i !in all.indices) return
        all[i] = all[i].copy(done = !all[i].done)
        store.saveTodos(all)
        listTick++
    }

    fun removeTodo(i: Int) {
        val all = store.loadTodos()
        if (i !in all.indices) return
        all.removeAt(i)
        store.saveTodos(all)
        listTick++
    }

    fun addNote(s: String) {
        val t = s.trim()
        if (t.isEmpty()) return
        store.addNote(t)
        listTick++
    }

    fun removeNote(s: String) {
        store.removeNote(s)
        listTick++
    }

    private fun runLists(arg: String): String {
        return when (val c = parseListCommand(arg)) {
            is AddTodo -> { addTodo(c.text); "Added to your list: “${c.text}”." }
            is DoneTodo -> {
                val all = store.loadTodos()
                if (c.index < 1 || c.index > all.size) "No todo #${c.index}. Say “my todos” to see them."
                else {
                    all[c.index - 1] = all[c.index - 1].copy(done = true)
                    store.saveTodos(all)
                    listTick++
                    "Done: “${all[c.index - 1].text}”."
                }
            }
            is RemoveTodo -> {
                val all = store.loadTodos()
                if (c.index < 1 || c.index > all.size) "No todo #${c.index}."
                else { val t = all[c.index - 1].text; removeTodo(c.index - 1); "Removed: “$t”." }
            }
            is ShowTodos -> {
                val all = store.loadTodos()
                if (all.isEmpty()) "Your list is empty. Say “add milk to my list”."
                else "Your list:\n" + all.mapIndexed { i, x -> "${i + 1}. ${if (x.done) "done" else "todo"} — ${x.text}" }.joinToString("\n")
            }
            is AddNote -> { addNote(c.text); "Noted: “${c.text}”." }
            is RemoveNote -> {
                val all = store.loadNotes()
                if (c.index < 1 || c.index > all.size) "No note #${c.index}."
                else { val t = all[c.index - 1]; removeNote(t); "Deleted note: “$t”." }
            }
            is ShowNotes -> {
                val all = store.loadNotes()
                if (all.isEmpty()) "No notes yet. Say “note …” to add one."
                else "Notes:\n" + all.mapIndexed { i, x -> "${i + 1}. $x" }.joinToString("\n")
            }
            null -> "Try “add milk to my list”, “my todos”, “done 2”, or “note …”."
        }
    }

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
        HudStateBus.update(online = brainOk)
        Router.detect(text)?.let { hit ->
            val reply = runTool(hit)
            messages.add(ChatMessage("bot", reply))
            speak(reply)
            persist()
            return
        }
        if (!brainOk) {
            val reply = "🔑 I need a Gemini API key for that (free from aistudio.google.com — add it in Settings ⚙️). Offline I can still do time, calculations, memory, reminders, device control, todos, and notes."
            messages.add(ChatMessage("bot", reply))
            speak(reply)
            persist()
            return
        }
        busy = true
        HudStateBus.update(thinking = true)
        HudStateBus.postTicker("[UPLINK: GEMINI]")
        val t0 = System.currentTimeMillis()
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
                HudStateBus.postTicker("[UPLINK: " + (System.currentTimeMillis() - t0) + "ms]")
            } catch (e: Exception) {
                messages.add(ChatMessage("bot", "⚠️ ${e.message}"))
            } finally {
                busy = false
                HudStateBus.update(thinking = false)
                persist()
            }
        }
    }

    private fun setSilence(on: Boolean): String {
        return try {
            val ctx = getApplication<Application>()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val i = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                "Almost — flip the toggle for Jarvis, then say silence again."
            } else {
                nm.setInterruptionFilter(
                    if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                if (on) "Silent mode on. Say unsilence to restore sound."
                else "Sound restored."
            }
        } catch (_: Exception) { "Couldn't change silent mode." }
    }

    private fun runDevice(arg: String): String {
        val cmd = parseDeviceCommand(arg)
            ?: return "I can open apps, flip the torch, dial contacts, or open settings — e.g. “open YouTube”."
        return when (cmd) {
            is OpenApp -> openAppByName(cmd.name)
            is Silence -> setSilence(true)
            is Unsilence -> setSilence(false)
            is Torch -> setTorch(cmd.on)
            is CallContact -> callContact(cmd.query)
            is WifiPanel -> openWifiPanel()
            is SysSettings -> openSysSettings()
        }
    }

    private fun openAppByName(name: String): String {
        return try {
            val ctx = getApplication<Application>()
            val pm = ctx.packageManager
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
            val q = name.lowercase().replace(" ", "")
            val hit = apps.firstOrNull {
                val l = it.loadLabel(pm)?.toString().orEmpty().lowercase().replace(" ", "")
                l == q || l.startsWith(q) || (l.isNotEmpty() && q.startsWith(l)) ||
                    it.activityInfo.packageName.lowercase().contains(q)
            } ?: apps.firstOrNull {
                it.loadLabel(pm)?.toString().orEmpty().lowercase().contains(name.lowercase())
            }
            if (hit == null) return "I couldn't find an app called “$name”."
            val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
                ?: return "Found it but couldn't launch it."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
            "Opening ${hit.loadLabel(pm)}."
        } catch (_: Exception) { "Couldn't open “$name”." }
    }

    private fun setTorch(on: Boolean): String {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.CAMERA
            return "I need camera permission for the torch — allow it, then ask again."
        }
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This device has no flashlight."
            cm.setTorchMode(id, on)
            if (on) "Torch on." else "Torch off."
        } catch (_: Exception) { "Couldn't reach the torch." }
    }

    private fun callContact(query: String): String {
        val ctx = getApplication<Application>()
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7 && digits.length >= query.trim().length - 2) {
            dialNumber(ctx, digits)
            return "Dialling $digits."
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.READ_CONTACTS
            return "I need contacts permission to find “$query” — allow it, then ask again."
        }
        val number = findContactNumber(ctx, query) ?: return "Couldn't find “$query” in contacts."
        dialNumber(ctx, number)
        return "Dialling $query."
    }

    private fun dialNumber(ctx: Context, number: String) {
        val i = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(number)))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun findContactNumber(ctx: Context, query: String): String? {
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$query%"), null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) { null }
    }

    private fun openWifiPanel(): String {
        return try {
            val ctx = getApplication<Application>()
            val i = if (android.os.Build.VERSION.SDK_INT >= 29) Intent(android.provider.Settings.Panel.ACTION_WIFI)
            else Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Opening Wi-Fi settings — Android doesn't let apps flip the switch directly."
        } catch (_: Exception) { "Couldn't open Wi-Fi settings." }
    }

    private fun openSysSettings(): String {
        return try {
            val ctx = getApplication<Application>()
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opening Settings."
        } catch (_: Exception) { "Couldn't open Settings." }
    }

    private fun scheduleReminder(req: ReminderRequest?): String {
        if (req == null) return "Tell me when — e.g. “remind me in 10 minutes to stretch” or “remind me at 5pm to gym”."
        val at = when (val w = req.whenAt) {
            is InMinutes -> System.currentTimeMillis() + w.minutes * 60_000L
            is AtTime -> atToMillis(w.hour, w.minute, w.tomorrow)
        }
        val now = System.currentTimeMillis()
        val items = store.loadReminders().filter { it.at > now }.toMutableList()
        val id = store.nextReminderId()
        items.add(ReminderItem(id, at, req.text))
        store.saveReminders(items)
        val ok = setReminderAlarm(id, at, req.text)
        val whenText = dueText(at, now)
        return if (ok) "I'll remind you $whenText: “${req.text}”."
        else "Saved ($whenText), but I couldn't set the alarm — allow notifications, then try again."
    }

    private fun atToMillis(h: Int, m: Int, tomorrow: Boolean): Long {
        val now = LocalDateTime.now()
        var dt = now.withHour(h).withMinute(m).withSecond(0).withNano(0)
        if (tomorrow || !dt.isAfter(now)) dt = dt.plusDays(1)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun setReminderAlarm(id: Int, at: Long, text: String): Boolean {
        return try {
            armReminderAlarm(getApplication(), id, at, text)
            true
        } catch (_: Exception) { false }
    }

    private fun listReminders(): String {
        val now = System.currentTimeMillis()
        val items = store.loadReminders().filter { it.at > now }.sortedBy { it.at }
        store.saveReminders(items)
        if (items.isEmpty()) return "No reminders set. Try “remind me in 10 minutes to stretch”."
        return "Reminders:\n" + items.mapIndexed { i, r -> "${i + 1}. ${dueText(r.at, now)} — ${r.text}" }
            .joinToString("\n") + "\nSay “cancel reminder N” to drop one."
    }

    private fun cancelReminder(arg: String): String {
        val n = arg.trim().toIntOrNull()
            ?: return "Say “cancel reminder N” — see numbers in “my reminders”."
        val items = store.loadReminders().filter { it.at > System.currentTimeMillis() }.sortedBy { it.at }
        val hit = items.getOrNull(n - 1) ?: return "No reminder #$n. Say “my reminders” to see them."
        try {
            val ctx = getApplication<Application>()
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, hit.id, Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            pi.cancel()
        } catch (_: Exception) { }
        store.removeReminder(hit.id)
        return "Cancelled: “${hit.text}”."
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
        "remind" -> scheduleReminder(parseReminder(hit.arg))
        "reminders" -> listReminders()
        "reminder_cancel" -> cancelReminder(hit.arg)
        "device" -> runDevice(hit.arg)
        "lists" -> runLists(hit.arg)
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

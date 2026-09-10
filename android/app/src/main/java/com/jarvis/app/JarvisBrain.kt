package com.jarvis.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit

// ---------- models ----------

data class ChatMessage(val role: String, val text: String) // role: user | bot

object Models {
    val FALLBACK = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-3-flash")
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

// ---------- on-device storage (key, model, facts, history) ----------

class Store(context: Context) {
    private val p = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v.trim()).apply()

    var model: String
        get() = p.getString("model", Models.FALLBACK[0]) ?: Models.FALLBACK[0]
        set(v) = p.edit().putString("model", v).apply()

    fun facts(): MutableList<String> =
        p.getStringSet("facts", emptySet())?.toMutableList() ?: mutableListOf()

    fun addFact(f: String) {
        val all = facts()
        all.add(0, f)
        p.edit().putStringSet("facts", all.take(50).toSet()).apply()
    }

    fun searchFacts(q: String): List<String> {
        val all = facts()
        if (q.isBlank()) return all.take(10)
        return all.filter { it.contains(q, ignoreCase = true) }.take(10)
    }

    /** History as (geminiRole, text) pairs, newest last. */
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

    fun saveHistory(h: List<Pair<String, String>>) {
        try {
            val arr = JSONArray()
            for ((r, t) in h.takeLast(40)) {
                arr.put(JSONObject().put("r", r).put("t", t.take(2000)))
            }
            p.edit().putString("history", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }
}

// ---------- Gemini REST API (direct, no SDK) ----------

object GeminiApi {
    class JarvisError(msg: String) : Exception(msg)

    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Tries each model in order. Returns (reply, modelUsed). */
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

        var lastErr = "unknown error"
        for (m in models) {
            val res = try {
                callOnce(m, apiKey, body)
            } catch (e: Exception) {
                Triple(false, "", "Network error: ${e.message}")
            }
            if (res.first) return@withContext res.second to m
            lastErr = res.third
            if (lastErr.startsWith("KEY:")) throw JarvisError(lastErr.removePrefix("KEY:"))
        }
        throw JarvisError("All models failed. Last error: $lastErr")
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
                    return Triple(false, "", "KEY:API key rejected. Open Settings (⚙️) and check your key.")
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

// ---------- ViewModel: chat state + tools + brain ----------

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
    var apiKey by mutableStateOf(store.apiKey)
        private set
    var model by mutableStateOf(store.model)
        private set

    val brainOk: Boolean get() = apiKey.isNotBlank()

    init {
        for ((r, t) in store.loadHistory()) {
            messages.add(ChatMessage(if (r == "user") "user" else "bot", t))
        }
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    "bot",
                    if (brainOk) "Hello. I am Jarvis. How can I help?"
                    else "Hello. I am Jarvis.\n\n🔑 Add your free Gemini key in Settings (⚙️, top right) to wake my brain. Meanwhile I can still tell time, calculate, and remember things — try 'what time is it?'"
                )
            )
        }
    }

    fun saveSettings(key: String, model: String) {
        store.apiKey = key
        store.model = model
        apiKey = store.apiKey
        this.model = store.model
        showSettings = false
        if (brainOk && messages.size == 1) {
            messages.add(ChatMessage("bot", "Brain connected. 🟢 What shall we do first?"))
            persist()
        }
    }

    fun orderedModels(): List<String> = listOf(model) + Models.FALLBACK.filter { it != model }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        messages.add(ChatMessage("user", text))
        persist()
        Router.detect(text)?.let { hit ->
            messages.add(ChatMessage("bot", runTool(hit)))
            persist()
            return
        }
        if (!brainOk) {
            messages.add(
                ChatMessage(
                    "bot",
                    "🔑 I need a Gemini API key for that (free from aistudio.google.com — paste it in Settings ⚙️). Offline I can still do time, calculations, and memory."
                )
            )
            persist()
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val facts = store.facts()
                val system = buildSystem(facts)
                val hist = messages.dropLast(1)
                    .map { (if (it.role == "user") "user" else "model") to it.text }
                val (reply, _) = GeminiApi.chat(apiKey, orderedModels(), system, hist, text)
                messages.add(ChatMessage("bot", reply))
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
        store.saveHistory(
            messages.map { (if (it.role == "user") "user" else "model") to it.text }
        )
    }
}

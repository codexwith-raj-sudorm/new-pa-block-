package com.jarvis.app

import com.jarvis.app.ui.ShotActivity
import android.Manifest
import android.app.AlarmManager
import android.app.SearchManager
import android.provider.AlarmClock
import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.app.local.StarkVaultDb
import com.jarvis.app.widget.StarkWidgetProvider
import com.jarvis.app.hardware.StarkDeviceController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
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

data class ChatMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis(), val imagePath: String? = null) // role: user | bot

/** Persisted message; img is a filesDir path for generated images ("" = none). */
data class StoredMsg(val r: String, val t: String, val ts: Long, val img: String = "")

data class ChatData(val id: String, var title: String, val msgs: MutableList<StoredMsg>)


/** Chat list title = first user message, truncated. Pure, tested. */
fun chatTitle(msgs: List<StoredMsg>): String {
    val first = msgs.firstOrNull { it.r == "user" }?.t?.trim().orEmpty()
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
        e = e.replace(Regex("""(?i)\bpercent\b"""), "%")
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
/** Compact bit codec for the export format tag (pure, tested). */
fun zwBits(hex: String): String {
    val sb = StringBuilder()
    for (c in hex.lowercase()) {
        val v = c.digitToIntOrNull(16) ?: continue
        for (b in 3 downTo 0) sb.append(if ((v shr b) and 1 == 1) '\u200c' else '\u200b')
    }
    return sb.toString()
}

fun zwRead(bits: String): String {
    val clean = bits.filter { it == '\u200b' || it == '\u200c' }
    val sb = StringBuilder()
    for (chunk in clean.chunked(4)) {
        if (chunk.length < 4) break
        var v = 0
        for (ch in chunk) v = v * 2 + (if (ch == '\u200c') 1 else 0)
        sb.append("0123456789abcdef"[v])
    }
    return sb.toString()
}

fun chatTranscript(title: String, msgs: List<ChatMessage>): String {
    val sb = StringBuilder("JARVIS - ")
    sb.append(title.ifBlank { "Chat" }).append(" \u00b7 j5-f4e3e575").append(zwBits("f4e3e575")).append("\n\n")
    for (m in msgs) {
        sb.append(if (m.role == "user") "You: " else "Jarvis: ")
        sb.append(m.text.trim()).append("\n\n")
    }
    return sb.toString().trimEnd() + "\n"
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

// ---------- everyday fun tools (pure, tested) ----------

/** Parse "roll a d20" / "roll dice" -> sides (default 6). Pure. */
fun parseDice(raw: String): Int {
    Regex("""d(\d{1,3})""").find(raw.lowercase())?.let {
        val n = it.groupValues[1].toIntOrNull() ?: 6
        if (n in 2..1000) return n
    }
    return 6
}

/** Roll a [sides] die from a 0..1 random value (pure for tests). */
fun rollDie(sides: Int, r: Double): Int = (r * sides).toInt().coerceIn(0, sides - 1) + 1

fun coinFace(heads: Boolean): String = if (heads) "Heads." else "Tails."

private val JOKES = listOf(
    "Why do programmers prefer dark mode? Because light attracts bugs.",
    "There are only 10 kinds of people: those who understand binary and those who don't.",
    "Why do Java developers wear glasses? Because they don't C#.",
    "My Wi-Fi went down for five minutes today. So I had to talk to my family. They seem like nice people.",
    "Why did the developer go broke? He used up all his cache.",
    "Why did the smartphone go to therapy? Too many unresolved notifications.",
    "I told my computer I needed a break. Now it keeps sending me KitKats.",
    "AI will never beat natural stupidity. Present company excepted, of course."
)

/** Joke by index (wraps around). Pure. */
fun jokeAt(i: Int): String = JOKES[Math.floorMod(i, JOKES.size)]

/** Clamp a user speech slider (0.5..2.0). Pure. */
fun clampSpeech(v: Float): Float = v.coerceIn(0.5f, 2.0f)

/** Persona base x user slider, clamped for the engine. Pure. */
fun effSpeech(base: Float, user: Float): Float = (base * user).coerceIn(0.25f, 4f)

/** Locale for the recognizer: Hindi when toggled, Indian English for English systems, else default. */
fun localeForListen(hindiListen: Boolean): Locale {
    if (hindiListen) return Locale.forLanguageTag("hi-IN")
    return if (Locale.getDefault().language == "en") Locale.forLanguageTag("en-IN")
    else Locale.getDefault()
}

/** Day-part greeting for an hour (0-23). Pure. */
fun daypart(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Burning the midnight oil"
}

/** "good morning" greeting (short messages only, so real questions win). Pure. */
fun daypartHit(low: String, t: String): Router.Hit? {
    if (t.length > 40) return null
    val g = listOf("good morning", "good afternoon", "good evening")
        .firstOrNull { low.startsWith(it) } ?: return null
    return Router.Hit("routine", g)
}

private val TO_M = mapOf(
    "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
    "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344
)
private val TO_G = mapOf(
    "mg" to 0.001, "g" to 1.0, "kg" to 1000.0,
    "oz" to 28.3495, "lb" to 453.592
)
private val TEMP = setOf("c", "f", "k")
private val UNIT_ALIAS = mapOf(
    "millimeter" to "mm", "millimeters" to "mm", "centimeter" to "cm", "centimeters" to "cm",
    "meter" to "m", "meters" to "m", "metre" to "m", "metres" to "m",
    "kilometer" to "km", "kilometers" to "km", "kilometre" to "km", "kilometres" to "km",
    "inch" to "in", "inches" to "in", "foot" to "ft", "feet" to "ft",
    "yard" to "yd", "yards" to "yd", "mile" to "mi", "miles" to "mi",
    "milligram" to "mg", "milligrams" to "mg", "gram" to "g", "grams" to "g",
    "kilogram" to "kg", "kilograms" to "kg", "ounce" to "oz", "ounces" to "oz",
    "pound" to "lb", "pounds" to "lb", "lbs" to "lb",
    "celsius" to "c", "fahrenheit" to "f", "kelvin" to "k", "celcius" to "c"
)

private fun normUnit(u: String): String? {
    val s = u.lowercase().trimEnd('.').replace("°", "")
    UNIT_ALIAS[s]?.let { return it }
    return if (s in TO_M || s in TO_G || s in TEMP) s else null
}

private fun trimNum(v: Double): String {
    val r = (v * 100).roundToInt() / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

/** Convert "5 miles to km" etc. Length, mass, temperature. Pure, tested. */
fun convertUnits(raw: String): String? {
    val m = Regex("""(-?\d+(?:\.\d+)?)\s*([a-zA-Z°]+)\s+(?:to|in)\s+([a-zA-Z°]+)""")
        .find(raw.trim()) ?: return null
    val v = m.groupValues[1].toDoubleOrNull() ?: return null
    val from = normUnit(m.groupValues[2]) ?: return null
    val to = normUnit(m.groupValues[3]) ?: return null
    val res: Double = if (from in TEMP && to in TEMP) {
        val c = when (from) { "f" -> (v - 32) * 5 / 9; "k" -> v - 273.15; else -> v }
        when (to) { "f" -> c * 9 / 5 + 32; "k" -> c + 273.15; else -> c }
    } else if (from in TO_M && to in TO_M) {
        v * TO_M.getValue(from) / TO_M.getValue(to)
    } else if (from in TO_G && to in TO_G) {
        v * TO_G.getValue(from) / TO_G.getValue(to)
    } else return null
    return "${trimNum(v)} $from = ${trimNum(res)} $to"
}

private val CUR_ALIAS = mapOf(
    "dollar" to "USD", "dollars" to "USD", "usd" to "USD",
    "buck" to "USD", "bucks" to "USD",
    "rupee" to "INR", "rupees" to "INR", "inr" to "INR", "rs" to "INR",
    "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
    "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP",
    "yen" to "JPY", "jpy" to "JPY",
    "yuan" to "CNY", "cny" to "CNY",
    "dirham" to "AED", "dirhams" to "AED", "aed" to "AED"
)

/** "100 dollars in rupees" -> (100, USD, INR). Pure, tested. */
fun parseCurrency(raw: String): Triple<Double, String, String>? {
    val m = Regex("""(?i)(-?\d+(?:\.\d+)?)\s*([a-z]+)\s+(?:to|in)\s+([a-z]+)""")
        .find(raw.trim()) ?: return null
    val v = m.groupValues[1].toDoubleOrNull() ?: return null
    val from = CUR_ALIAS[m.groupValues[2].lowercase()] ?: return null
    val to = CUR_ALIAS[m.groupValues[3].lowercase()] ?: return null
    if (from == to) return null
    return Triple(v, from, to)
}

/** Extract rates.{CCY} from frankfurter.app JSON. Pure, tested. */
fun parseFxRate(json: String, to: String): Double? {
    return try {
        val r = JSONObject(json).getJSONObject("rates").getDouble(to)
        if (r.isNaN() || r <= 0) null else r
    } catch (_: Exception) { null }
}

/** "100 USD = 8,320.50 INR". Pure, tested. */
fun formatFx(v: Double, from: String, rate: Double, to: String): String {
    val out = v * rate
    val disp = if (out >= 1000) "%,.2f".format(out) else trimNum(out)
    return "${trimNum(v)} $from = $disp $to"
}

/** 90 -> "1 min 30 sec". Pure. */
fun fmtDur(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val parts = mutableListOf<String>()
    if (h > 0) parts.add("$h h")
    if (m > 0) parts.add("$m min")
    if (s > 0 || parts.isEmpty()) parts.add("$s sec")
    return parts.joinToString(" ")
}

/** Extract a city from "weather in X" / "X weather" / "rain in X". Pure, tested. */
fun parseWeatherCity(raw: String): String {
    val t = raw.trim().trimEnd('?', '.', '!').trim()
    val pats = listOf(
        Regex("""(?i)\bweather\s+(?:in|at|for)\s+(.+)"""),
        Regex("""(?i)\bforecast\s+(?:in|at|for)?\s*(.+)"""),
        Regex("""(?i)^(.+?)\s+weather$"""),
        Regex("""(?i)\brain\s+(?:in|at)\s+(.+)""")
    )
    val leadQ = Regex("""(?i)^(what'?s?|what\s+is|how'?s?|how\s+is|will|is|the|a|an)\s+""")
    val trailT = Regex("""(?i)\s+(today|tonight|tomorrow|right\s+now|this\s+\w+)$""")
    for (p in pats) {
        var c = p.find(t)?.groupValues?.get(1)?.trim() ?: continue
        c = trailT.replace(c, "").trim()
        var prev: String
        do { prev = c; c = leadQ.replace(c, "").trim() } while (c != prev)
        if (c.equals("the", true) || c.equals("a", true) || c.equals("an", true)) c = ""
        c = Regex("""(?i)^(in|at|for)\s+""").replace(c, "").trim()
        if (c.isNotEmpty() && c.length <= 60 && !c.contains("\n")) return c
    }
    return ""
}

/** Current conditions from wttr.in (keyless JSON). */
data class WttrNow(
    val area: String, val country: String, val tempC: String,
    val feelsC: String, val desc: String, val humidity: String, val windKph: String
)

/** Parse wttr.in j1 JSON (null when unparseable). Pure, tested. */
fun parseWttr(json: String): WttrNow? {
    return try {
        val o = JSONObject(json)
        val cur = o.getJSONArray("current_condition").getJSONObject(0)
        val near = o.getJSONArray("nearest_area").getJSONObject(0)
        WttrNow(
            area = near.getJSONArray("areaName").getJSONObject(0).optString("value"),
            country = near.getJSONArray("country").getJSONObject(0).optString("value"),
            tempC = cur.optString("temp_C"),
            feelsC = cur.optString("FeelsLikeC"),
            desc = cur.getJSONArray("weatherDesc").getJSONObject(0).optString("value"),
            humidity = cur.optString("humidity"),
            windKph = cur.optString("windspeedKmph")
        )
    } catch (_: Exception) { null }
}

/** "Mumbai, India: Partly cloudy, 31C (feels 34C)...". Pure, tested. */
fun formatWeather(w: WttrNow): String {
    val deg = "°C"
    val where = listOf(w.area, w.country).filter { it.isNotBlank() }.joinToString(", ")
    return "$where: ${w.desc}, ${w.tempC}$deg (feels ${w.feelsC}$deg). " +
        "Humidity ${w.humidity}%, wind ${w.windKph} km/h."
}

/** A user-defined smart action: spoken name -> HTTP call. */
data class HookAction(val name: String, val url: String, val method: String)

/** Match "turn on the bedroom light" to a webhook (longest name wins). Pure, tested. */
fun matchHook(text: String, hooks: List<HookAction>): HookAction? {
    val words = text.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
        .split(" ").filter { it.isNotEmpty() }.toSet()
    var best: HookAction? = null
    var bestLen = 0
    for (h in hooks) {
        val need = h.name.lowercase().split(" ").filter { it.isNotEmpty() }
        if (need.isEmpty()) continue
        if (need.all { it in words } && need.size > bestLen) {
            best = h
            bestLen = need.size
        }
    }
    return best
}

/** Build the master-card JSON shared between a master's devices. Pure, tested. */
fun masterCardJson(key: String, name: String, about: String): String =
    JSONObject().put("k", key).put("n", name).put("a", about).toString()

/** Parse master-card JSON into key/name/about (null when invalid). Pure, tested. */
fun parseMasterCardJson(json: String): Triple<String, String, String>? {
    return try {
        val o = JSONObject(json.trim())
        val k = o.optString("k", "")
        if (k.length < 4) return null
        Triple(k, o.optString("n", ""), o.optString("a", ""))
    } catch (_: Exception) { null }
}

/** Wrap a code block with its language fence for sharing (pure, tested). */
fun codeShareText(lang: String, code: String): String =
    "```" + lang.trim().ifEmpty { "code" } + "\n" + code + "\n```"

/** Sanitize a chat title (pure, tested). */
fun cleanTitle(t: String): String = t.trim().take(40).ifEmpty { "New chat" }

/** Build a full JSON backup of user data (pure, tested). */
fun buildBackup(
    chats: List<ChatData>,
    facts: List<String>,
    todos: List<TodoItem>,
    notes: List<String>,
    hooks: List<HookAction>,
    reminders: List<ReminderItem>
): String {
    val o = JSONObject()
    o.put("app", "jarvis")
    o.put("v", 1)
    o.put("fmt", "j5-c9a5649b") // export format tag
    o.put("at", System.currentTimeMillis())
    val carr = JSONArray()
    for (c in chats) {
        val co = JSONObject().put("title", c.title)
        val marr = JSONArray()
        for ((r, t, ts) in c.msgs.takeLast(500)) {
            marr.put(JSONArray().put(r).put(t).put(ts))
        }
        co.put("msgs", marr)
        carr.put(co)
    }
    o.put("chats", carr)
    o.put("facts", JSONArray(facts))
    val tarr = JSONArray()
    for (t in todos) tarr.put(JSONObject().put("text", t.text).put("done", t.done))
    o.put("todos", tarr)
    o.put("notes", JSONArray(notes))
    val harr = JSONArray()
    for (h in hooks) harr.put(JSONObject().put("n", h.name).put("u", h.url).put("m", h.method))
    o.put("hooks", harr)
    val rarr = JSONArray()
    for (r in reminders) rarr.put(JSONObject().put("at", r.at).put("text", r.text))
    o.put("reminders", rarr)
    return o.toString()
}

/** Core identity injected when a master key is installed. Pure, tested. */
fun masterIdentity(name: String, about: String): String {
    val who = name.ifBlank { "Master" }
    val sb = StringBuilder("Your master and creator is $who. You were created by them, and you recognize this user as your Master. Address them as Master or $who.")
    if (about.isNotBlank()) sb.append(" What you know about your Master: $about")
    return sb.toString()
}

/** Reverse the build-time key obfuscation (reversed Base64). Pure, tested. */
fun unobscureKey(obf: String): String = try {
    if (obf.isBlank()) "" else String(
        java.util.Base64.getDecoder().decode(obf.trim()), Charsets.UTF_8
    ).reversed()
} catch (_: Exception) {
    ""
}

// ---------- offline tool router (pure Kotlin, unit-tested) ----------

object Router {
    data class Hit(val tool: String, val arg: String)

    private val mathy = Regex("""^[\d\s+\-*/().%^!]+$""")

    fun detect(raw: String): Hit? {
        val t = raw.trim()
        val low = t.lowercase()
        // Image generation first: strict verb-led match, never steals other commands.
        genImagePromptOf(low)?.let { return Hit("gen_image", it) }
        // ——— Jarvis v5.9 scheduled messaging (must beat time/device rules) ———
        parseScheduledMessage(t)?.let { return Hit("sched_msg", t) }
        if (low == "scheduled messages" || low == "my scheduled messages" ||
            low == "list scheduled" || low.startsWith("list scheduled ")
        ) return Hit("sched_list", "")
        Regex("""\b(cancel|delete|remove) scheduled (?:message )?(\d+)""").find(low)?.let {
            return Hit("sched_cancel", it.groupValues[2])
        }
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
        if ("flip a coin" in low || "coin flip" in low || low == "flip coin") return Hit("coin", "")
        if (low.startsWith("roll ")) return Hit("dice", t)
        convertUnits(t)?.let { return Hit("convert", t) }
        parseCurrency(t)?.let { return Hit("fx", t) }
        if ("joke" in low && t.length < 60) return Hit("joke", "")
        if (low.contains("weather") || low.contains("forecast") ||
            Regex("""\brain\b""").containsMatchIn(low)
        ) return Hit("weather", t)
        daypartHit(low, t)?.let { return it }
        if (low.contains("notification") && ("read" in low || "check" in low || "my" in low || "any" in low || low == "notifications")) return Hit("notifs", "")
        // Settings features, promoted to voice tools.
        if (low.contains("hands-free") || low.contains("handsfree") || low.contains("hands free")) return Hit("handsfree", t)
        if (low.contains("daily briefing")) return Hit("dailybrief", t)
        if (low.contains("briefing")) return Hit("briefing", t)
        if (low.contains("smart action")) return Hit("hooks", t)
        val micWord = low.contains("mic") || low.contains("listen") || low.contains("mode")
        if ((micWord && (low.contains("hindi") || low.contains("auto") || low.contains("english"))) ||
            (low.contains("understand") && low.contains("hindi"))
        ) return Hit("miclang", t)
        if (low.startsWith("open reminders") || low == "show reminders") return Hit("reminders_ui", t)
        if (low.contains("backup") || low.contains("back up")) return Hit("backup", t)
        if (low.contains("battery")) return Hit("battery", t)
        if (low.contains("autostart") || low.contains("auto-start") || low.contains("auto start")) return Hit("autostart", t)
        if (low.contains("voice guard") || low.contains("voiceguard")) return Hit("voiceguard", t)
        if (low.contains("voiceprint") || low.contains("voice print")) return Hit("voiceprint", t)
        if (low.contains("what's new") || low.contains("whats new") || low.contains("changelog") || low.contains("change log")) return Hit("whatsnew", t)
        if (low.contains("what can you do") || Regex("""^help[?.!]*$""").matches(low)) return Hit("help", t)
        // GitHub (read-only): repos, status, builds, issues, files.
        if (low.contains("my repos") || low.contains("github repos") || low.startsWith("list repos") ||
            low == "repos" || low.contains("list my repositor")
        ) return Hit("github_repos", "")
        Regex("""^repo (.+)$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_repo", it) }
        }
        Regex("""^open (.+) repo$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_repo", it) }
        }
        Regex("""^(.+) repo status$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_repo", it) }
        }
        if (low == "build status") return Hit("github_builds", "")
        Regex("""^is (.+) building$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_builds", it) }
        }
        Regex("""^(.+) build status$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_builds", it) }
        }
        Regex("""^(?:check|show)(?: the)? builds?(?: for| of)? (.+)$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_builds", it) }
        }
        Regex("""^(?:show|list|get)(?: me)?(?: the)? issues (?:of|for|in) (.+)$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_issues", it) }
        }
        Regex("""^(.+) (?:repo )?issues$""").find(low)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.contains("github") || q.contains("repo") || q.contains("open")) {
                repoName(q.replace("github", "").replace("repo", "").replace("open", "").trim())
                    ?.let { return Hit("github_issues", it) }
            }
        }
        Regex("""^read (.+?) from (.+)$""").find(low)?.let { m ->
            val file = m.groupValues[1].trim()
            if (file.contains(".") || file.contains("/") || file.contains("readme")) {
                repoName(m.groupValues[2])?.let { return Hit("github_read", "$file|$it") }
            }
        }
        Regex("""^show (?:the )?readme (?:of|from|in) (.+)$""").find(low)?.let { m ->
            repoName(m.groupValues[1])?.let { return Hit("github_read", "README|$it") }
        }
        // Screen: screenshots + vision watch + accessibility control.
        if (low == "screenshot" || low == "take screenshot" || low.startsWith("screenshot ") ||
            low.contains("take a screenshot") || low.contains("capture screen") ||
            low.contains("share my screen")
        ) return Hit("shot", "")
        if (low.contains("accessibility")) return Hit("access_setup", "")
        if (low.contains("on my screen") || low == "read screen" || low == "read my screen" ||
            low.contains("what is on my screen") || low.contains("what's on my screen") ||
            low.contains("look at my screen") || low.contains("see my screen") ||
            low.contains("watch my screen") || low.contains("describe my screen") ||
            low.contains("read this screen") || low.contains("what am i looking at")
        ) return Hit("screen_watch", t)
        Regex("""^tap (.+)$""").find(low)?.let {
            val q = it.groupValues[1].trim().trimEnd('?', '.', '!').trim()
            if (q.isNotEmpty()) return Hit("access_tap", q)
        }
        Regex("""^scroll (up|down)$""").find(low)?.let { return Hit("access_scroll", it.groupValues[1]) }
        if (low == "go back" || low == "press back" || low == "back button") return Hit("access_back", "")
        if (low == "recent apps" || low == "recents" || low == "show recents" ||
            low == "show recent apps" || low == "open recent apps"
        ) return Hit("access_recents", "")
        Regex("""^(?:open|launch|start)\s+(.+?)\s+from\s+(?:the\s+)?recents?$""").find(low)?.let {
            val q = it.groupValues[1].trim().trimEnd('?', '.', '!').trim()
            if (q.isNotEmpty()) return Hit("access_recents_tap", q)
        }
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

    var wakeEnabled: Boolean
        get() = p.getBoolean("wake", false)
        set(v) = p.edit().putBoolean("wake", v).apply()

    var hindiListen: Boolean
        get() = p.getBoolean("listen_hi", false)
        set(v) = p.edit().putBoolean("listen_hi", v).apply()

    var voiceGuard: Boolean
        get() = p.getBoolean("voice_guard", true)
        set(v) = p.edit().putBoolean("voice_guard", v).apply()

    var vp_templates: String
        get() = p.getString("vp_templates", "") ?: ""
        set(v) = p.edit().putString("vp_templates", v).apply()

    var vp_base: Float
        get() = p.getFloat("vp_base", -1f)
        set(v) = p.edit().putFloat("vp_base", v).apply()

    var vp_mult: Float
        get() = p.getFloat("vp_mult", 1.8f)
        set(v) = p.edit().putFloat("vp_mult", v).apply()

    var vp_phrase: String
        get() = p.getString("vp_phrase", "") ?: ""
        set(v) = p.edit().putString("vp_phrase", v).apply()

    var masterUnlocked: Boolean
        get() = p.getBoolean("master_unlocked", false)
        set(v) = p.edit().putBoolean("master_unlocked", v).apply()

    var dailyBriefing: Boolean
        get() = p.getBoolean("brief_daily", false)
        set(v) = p.edit().putBoolean("brief_daily", v).apply()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var masterKey: String
        get() = p.getString("master_key", "") ?: ""
        set(v) = p.edit().putString("master_key", v).apply()

    var masterName: String
        get() = p.getString("master_name", "") ?: ""
        set(v) = p.edit().putString("master_name", v).apply()

    var masterAbout: String
        get() = p.getString("master_about", "") ?: ""
        set(v) = p.edit().putString("master_about", v).apply()

    var lastWakeGreet: String
        get() = p.getString("wake_greet", "") ?: ""
        set(v) = p.edit().putString("wake_greet", v).apply()

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
            StarkVaultDb.remember(appCtx, "memory", t)
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

    fun loadSchedMsgs(): MutableList<SchedMsgItem> {
        val out = mutableListOf<SchedMsgItem>()
        try {
            val arr = JSONArray(p.getString("schedmsgs_v1", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    SchedMsgItem(
                        o.optInt("id", i), o.optLong("at", 0), o.optString("app", "sms"),
                        o.optString("label", ""), o.optString("number", ""), o.optString("body", "")
                    )
                )
            }
        } catch (_: Exception) { }
        return out
    }

    fun saveSchedMsgs(list: List<SchedMsgItem>) {
        try {
            val arr = JSONArray()
            for (m in list) arr.put(
                JSONObject().put("id", m.id).put("at", m.at).put("app", m.app)
                    .put("label", m.label).put("number", m.number).put("body", m.body)
            )
            p.edit().putString("schedmsgs_v1", arr.toString()).apply()
        } catch (_: Exception) { }
    }

    fun removeSchedMsg(id: Int) {
        saveSchedMsgs(loadSchedMsgs().filterNot { it.id == id })
    }

    fun nextSchedMsgId(): Int {
        val n = p.getInt("schedmsg_seq", 100001)
        p.edit().putInt("schedmsg_seq", n + 1).apply()
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

    fun loadHooks(): MutableList<HookAction> {
        val out = mutableListOf<HookAction>()
        try {
            val arr = JSONArray(p.getString("hooks_v1", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(HookAction(o.getString("n"), o.getString("u"), o.optString("m", "GET")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveHooks(list: List<HookAction>) {
        try {
            val arr = JSONArray()
            for (h in list.take(30)) {
                arr.put(JSONObject().put("n", h.name.take(40)).put("u", h.url.take(500)).put("m", h.method))
            }
            p.edit().putString("hooks_v1", arr.toString()).apply()
        } catch (_: Exception) {
        }
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
                val msgs = mutableListOf<StoredMsg>()
                for (j in 0 until marr.length()) {
                    val m = marr.getJSONObject(j)
                    msgs.add(StoredMsg(m.getString("r"), m.getString("t"), m.optLong("ts", 0), m.optString("img", "")))
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
                for ((r, t, ts, img) in c.msgs.takeLast(40)) {
                    val mo = JSONObject().put("r", r).put("t", t.take(2000)).put("ts", ts)
                    if (img.isNotEmpty()) mo.put("img", img)
                    marr.put(mo)
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
    private val imgClient by lazy { client.newBuilder().callTimeout(120, TimeUnit.SECONDS).build() }
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

    /** Single-turn vision call: user text + one JPEG, same model fallback. */
    suspend fun chatWithImage(
        apiKey: String,
        models: List<String>,
        system: String,
        user: String,
        imageBase64: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", user))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().put("mime_type", "image/jpeg").put("data", imageBase64)
                                )
                            )
                    )
                )
            )
            .put("generationConfig", JSONObject().put("maxOutputTokens", 512))
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
            errs.add("$m -> ${res.third.take(150)}")
        }
        throw JarvisError("Vision failed on all ${models.size} models:\n" + errs.joinToString("\n") { "\u2022 $it" })
    }

    /** Image generation: tries image models first, returns (mime, bytes). */
    suspend fun generateImage(apiKey: String, models: List<String>, prompt: String): Pair<String, ByteArray> =
        withContext(Dispatchers.IO) {
            val body = genImageRequestBody(prompt)
            val errs = mutableListOf<String>()
            for (m in models) {
                try {
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey")
                        .post(body.toRequestBody(JSON)).build()
                    imgClient.newCall(req).execute().use { resp ->
                        val txt = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            if (resp.code == 400 && ("API key" in txt || "API_KEY" in txt))
                                throw JarvisError("API key rejected. Open Settings (\u2699\ufe0f) and check the key.")
                            errs.add("$m -> HTTP ${resp.code}: ${txt.take(150)}")
                            return@use
                        }
                        val got = parseGenImageData(txt)
                        if (got != null) return@withContext got
                        errs.add("$m -> no image in reply (blocked or unsupported)")
                    }
                } catch (e: JarvisError) {
                    throw e
                } catch (e: Exception) {
                    errs.add("$m -> Network error: ${e.message?.take(100)}")
                }
            }
            throw JarvisError("Image generation failed:\n" + errs.joinToString("\n") { "\u2022 $it" })
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
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    val messages = mutableStateListOf<ChatMessage>()
    val availableModels = mutableStateListOf<String>()
    val chats = mutableStateListOf<ChatData>()
    var dashTemp by mutableStateOf("\u2014")
    var dashPing by mutableStateOf("\u2014")
    var dashBatt by mutableStateOf(-1)
    var busy by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
    var showChats by mutableStateOf(false)
    var showMemory by mutableStateOf(false)
    var showList by mutableStateOf(false)
    var showWhatsNew by mutableStateOf(false)
    var showBriefing by mutableStateOf(false)
    var showHooks by mutableStateOf(false)
    var showReminders by mutableStateOf(false)
    var remTick by mutableStateOf(0)
    var showOnboard by mutableStateOf(!store.onboarded)
    var masterInstalled by mutableStateOf(store.masterKey.isNotBlank())
    var masterName by mutableStateOf(store.masterName)
    var masterAbout by mutableStateOf(store.masterAbout)
    var hookTick by mutableStateOf(0)
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
    var hindiListen by mutableStateOf(store.hindiListen)
    var voiceGuard by mutableStateOf(store.voiceGuard)
    var dailyBriefing by mutableStateOf(store.dailyBriefing)
        private set
    var masterUnlocked by mutableStateOf(store.masterUnlocked)
        private set
    var listening by mutableStateOf(false)
        private set
    var permRequest by mutableStateOf<String?>(null)
    var wakeOn by mutableStateOf(WakeService.isRunning)
        private set

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var listenTries = 0
    private var vpCapture: VoiceCapture? = null
    private var vpCache: List<List<FloatArray>>? = null
    private var enrollingVp = false
    var convoActive by mutableStateOf(false)
        private set
    private var convoErrs = 0
    private var lastSpeakMs = 0L
    private var speakGen = 0
    private var liveOnCall = false
    private var convoLastVoiceMs = 0L
    private var utterPeakRms = 0f
    private var convoWatchdog: kotlinx.coroutines.Job? = null
    var batteryFixTick by mutableStateOf(0)
    var batteryStateTick by mutableStateOf(0)
    var lastHeard by mutableStateOf("")
    var heardFresh by mutableStateOf(false)
    var voiceNote by mutableStateOf<String?>(null)
    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).build()
    }

    // Owner key, baked at build time from the GEMINI_API_KEY repo secret
    // (stored reversed+Base64 so it isn't plainly greppable inside the APK).
    // It is completely invisible in the UI: no screen mentions it.
    // NOTE: obfuscation, not encryption — anyone decompiling the APK can recover it.
    // Real protection = restrict the key in Google Cloud + keep the APK private.
    private val builtinKey: String = unobscureKey(BuildConfig.DEFAULT_GEMINI_KEY)

    /** User's own key if pasted, else the built-in key — only once the Master Key is installed. */
    private val effectiveKey: String get() = apiKey.ifBlank { if (masterInstalled) builtinKey else "" }

    // Owner GitHub token, baked from the GH_READ_TOKEN repo secret (same obfuscation).
    // Only usable once the Master Key is installed — that activation is the unlock.
    // NOTE: obfuscation, not encryption — use a read-only token and keep the APK private.
    private val builtinGh: String = unobscureKey(BuildConfig.DEFAULT_GITHUB_TOKEN)

    /** Pasted token first, else the baked token once the Master Key is installed. */
    val effectiveGithubToken: String
        get() = githubToken.ifBlank { if (masterInstalled) builtinGh else "" }

    fun githubStatus(): String = when {
        githubToken.isNotBlank() -> "✓ Custom token active"
        builtinGh.isNotBlank() && masterInstalled -> "✓ Repo access active via Master Key"
        builtinGh.isNotBlank() -> "Install Master Key to activate repo access"
        else -> ""
    }

    val brainOk: Boolean get() = effectiveKey.isNotBlank()

    init {
        installBakedMaster()
        StarkVaultDb.purgeExpired(getApplication<Application>().applicationContext)
        refreshDashboard()
        val cached = store.cachedModels()
        availableModels.addAll(cached.ifEmpty { Models.FALLBACK })
        val loaded = store.loadChats()
        if (loaded.isEmpty()) {
            val legacy = store.loadHistory()
            if (legacy.isNotEmpty()) {
                loaded.add(ChatData("c1", chatTitle(legacy.map { StoredMsg(it.first, it.second, 0L) }), legacy.map { StoredMsg(it.first, it.second, 0L) }.toMutableList()))
            } else {
                loaded.add(ChatData("c1", "New chat", mutableListOf()))
            }
            store.removeLegacyHistory()
        }
        chats.addAll(loaded)
        val savedId = store.loadActiveId()
        activeChatId = if (loaded.any { it.id == savedId }) savedId else loaded[0].id
        val active = loaded.first { it.id == activeChatId }
        for ((r, t, ts, img) in active.msgs) {
            messages.add(ChatMessage(if (r == "user") "user" else "bot", t, ts, if (img.isEmpty()) null else img))
        }
        if (messages.isEmpty()) {
            messages.add(ChatMessage("bot", greet()))
        }
        createTts("com.google.android.tts")
        viewModelScope.launch {
            CallStateBus.inCall.collect { onCall ->
                if (onCall) {
                    liveOnCall = listening || convoActive
                    stopSpeaking()
                    if (convoActive) endConvoSession() else stopListening()
                } else if (liveOnCall) {
                    liveOnCall = false
                    if (!showSettings) {
                        try { startListening() } catch (_: Exception) { }
                    }
                }
            }
        }
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

    private fun greet(): String {
        if (masterInstalled) return masterGreet(masterName)
        return if (brainOk) "Hello. I am Jarvis. How can I help?"
        else "Hello. I am Jarvis.\n\n🔑 Add a Gemini key in Settings (⚙️, top right) to wake my brain — free from aistudio.google.com. Meanwhile I can still tell time, calculate, and remember things — try 'what time is it?'"
    }

    // ---- voice output (Jarvis-style male voice, human prosody) ----

    private fun createTts(engine: String?) {
        try {
            tts = TextToSpeech(getApplication(), { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyVoice()
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
            val key = "priya" // fixed voice
            val match = resolveEngineVoice(t, key)
            if (match != null) t.voice = match
            else t.language = Locale.getDefault()
            t.setSpeechRate(0.93f) // fixed
            t.setPitch(0.68f) // fixed
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { SpeechState.speaking = true; HudStateBus.update(speaking = true) }
                override fun onDone(id: String?) { if (id == lastUtteranceId()) { SpeechState.speaking = false; HudStateBus.update(speaking = false); if ((continuous || convoActive) && ttsOn && !showSettings) Handler(Looper.getMainLooper()).post { try { startListening() } catch (_: Exception) {} } } }
                override fun onError(id: String?) { if (id == null || id == lastUtteranceId()) { SpeechState.speaking = false; HudStateBus.update(speaking = false) } }
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

    fun setMasterUnlocked() {
        store.masterUnlocked = true
        masterUnlocked = true
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

    fun toggleHindiListen() {
        hindiListen = !hindiListen
        store.hindiListen = hindiListen
    }

    fun toggleDailyBriefing() {
        dailyBriefing = !dailyBriefing
        store.dailyBriefing = dailyBriefing
        try {
            armDailyBriefing(getApplication(), dailyBriefing)
        } catch (_: Exception) {
        }
    }

    fun reminderItems(): List<ReminderItem> {
        val now = System.currentTimeMillis()
        val items = store.loadReminders().filter { it.at > now }.sortedBy { it.at }
        store.saveReminders(items)
        return items
    }

    fun deleteReminder(id: Int) {
        try {
            val ctx = getApplication<Application>()
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            pi.cancel()
        } catch (_: Exception) { }
        store.removeReminder(id)
        remTick++
    }

    /** Add a reminder from dialog text (quick-add). Returns the confirmation line. */
    fun addReminderText(text: String): String {
        val msg = scheduleReminder(parseReminder(reminderInput(text)))
        remTick++
        return msg
    }

    fun finishOnboard() {
        store.onboarded = true
        showOnboard = false
    }

    fun installMaster(key: String, name: String, about: String) {
        val k = key.trim()
        if (k.length < 4) {
            settingsMsg = "Master key needs at least 4 characters."
            return
        }
        val baked = k == BAKED_MASTER_KEY
        store.masterKey = k
        store.masterName = (if (baked) BAKED_MASTER_NAME else MASTER_SELF_NAME).take(40)
        store.masterAbout = (if (baked) BAKED_MASTER_ABOUT else about.trim().ifBlank { MASTER_SELF_ABOUT }).take(500)
        masterInstalled = true
        masterName = store.masterName
        masterAbout = store.masterAbout
        store.masterUnlocked = false
        masterUnlocked = false
        settingsMsg = if (baked) "Master key accepted. Welcome, Master Raj."
        else "Master key installed. I recognize you, Master Raj."
        HudStateBus.postTicker("[MASTER RECOGNIZED]")
    }

    fun removeMaster(attempt: String): Boolean {
        if (attempt != store.masterKey) {
            settingsMsg = "Wrong master key."
            return false
        }
        store.masterKey = ""
        store.masterName = ""
        store.masterAbout = ""
        masterName = ""
        masterAbout = ""
        masterInstalled = false
        settingsMsg = "Master key removed. Normal mode."
        return true
    }

    fun shareMasterCard() {
        try {
            val json = masterCardJson(store.masterKey, store.masterName, store.masterAbout)
            val card = "JARVIS-MASTER:" + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(Intent.createChooser(i, "Share master card"))
        } catch (_: Exception) {
            settingsMsg = "Couldn't build the master card."
        }
    }

    fun importMasterCard(text: String) {
        try {
            val b64 = text.trim().removePrefix("JARVIS-MASTER:")
            if (b64.isBlank()) {
                settingsMsg = "Paste a master card first."
                return
            }
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val card = parseMasterCardJson(json)
            if (card == null) {
                settingsMsg = "That card didn't scan. Check and retry."
                return
            }
            installMaster(card.first, card.second, card.third)
        } catch (_: Exception) {
            settingsMsg = "That card didn't scan. Check and retry."
        }
    }

    private fun installBakedMaster() {
        if (store.masterKey.isNotBlank()) return
        val b64 = BuildConfig.DEFAULT_MASTER
        if (b64.isBlank()) return
        try {
            val json = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val card = parseMasterCardJson(json) ?: return
            store.masterKey = card.first
            store.masterName = card.second.take(40)
            store.masterAbout = card.third.take(500)
            masterInstalled = true
            masterName = store.masterName
            masterAbout = store.masterAbout
        } catch (_: Exception) {
        }
    }

    fun hooks(): List<HookAction> = store.loadHooks()

    fun addHook(name: String, url: String, method: String) {
        val n = name.trim().take(40)
        val u = url.trim().take(500)
        if (n.isEmpty() || !(u.startsWith("http://") || u.startsWith("https://"))) {
            toast("Give the action a name and an http(s) URL.")
            return
        }
        val all = store.loadHooks()
        all.removeAll { it.name.equals(n, ignoreCase = true) }
        all.add(0, HookAction(n, u, if (method == "POST") "POST" else "GET"))
        store.saveHooks(all)
        hookTick++
    }

    fun removeHook(name: String) {
        val all = store.loadHooks()
        all.removeAll { it.name.equals(name, ignoreCase = true) }
        store.saveHooks(all)
        hookTick++
    }

    private suspend fun fireHook(h: HookAction): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(h.url)
        if (h.method == "POST") b.post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
        http.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP " + resp.code)
            "Done — " + h.name + " triggered."
        }
    }

    fun exportBackup() {
        try {
            val json = buildBackup(
                store.loadChats(), store.facts(), store.loadTodos(),
                store.loadNotes(), store.loadHooks(), store.loadReminders()
            )
            val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, json)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(Intent.createChooser(i, "Backup Jarvis data"))
        } catch (_: Exception) {
            toast("Backup failed — history too large to share")
        }
    }

    // ---- screen watch (vision): capture a frame, describe it ----

    private var lastWatchErr: String? = null
    private var lastWatchUsedCache = false

    /** Async vision answer for "what's on my screen". Falls back to text read. */
    private suspend fun watchScreen(question: String): String {
        if (!brainOk) return AccessBridge.read() ?: needAccess()
        val img = captureScreenForWatch()
        if (img == null) {
            val t = AccessBridge.read()
            return if (t != null) "Couldn't capture the screen for vision — here's the text I can read:\n$t"
            else needAccess()
        }
        return try {
            val system = "You are Jarvis describing the user's phone screen.\n" + buildSystem(store.facts())
            val prompt = visionPromptFor(question)
            val (reply, _) = try {
                GeminiApi.chatWithImage(effectiveKey, resolveModels(false), system, prompt, img)
            } catch (e: GeminiApi.JarvisError) {
                if (!e.message.orEmpty().contains("404")) throw e
                GeminiApi.chatWithImage(effectiveKey, resolveModels(true), system, prompt, img)
            }
            reply
        } catch (e: Exception) {
            "Couldn't analyze the screen (${e.message?.take(120)})." +
                (AccessBridge.read()?.let { "\n\nHere's the text I can read:\n$it" } ?: "")
        }
    }

    /** Capture one JPEG for vision (cached consent, else prompt). Base64 or null. */
    private suspend fun captureScreenForWatch(): String? {
        var path = awaitWatchCapture()
        if (path == null && lastWatchUsedCache && lastWatchErr != null && watchErrorNeedsReprompt(lastWatchErr!!)) {
            ScreenConsent.code = 0
            ScreenConsent.data = null
            path = awaitWatchCapture()
        }
        if (path == null) return null
        val b64 = withContext(Dispatchers.IO) {
            try {
                val bytes = java.io.File(path).readBytes()
                if (bytes.isEmpty()) null else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Exception) {
                null
            }
        }
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {
        }
        return b64
    }

    /** One capture attempt: returns the JPEG path or null (sets lastWatchErr). */
    private suspend fun awaitWatchCapture(): String? {
        val app = getApplication<Application>()
        val done = CompletableDeferred<String?>()
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                cancelCaptureNotif()
                if (code == 0) done.complete(data?.getString("path"))
                else {
                    lastWatchErr = data?.getString("error") ?: "failed"
                    done.complete(null)
                }
            }
        }
        lastWatchErr = null
        lastWatchUsedCache = ScreenConsent.code != 0 && ScreenConsent.data != null
        try {
            if (lastWatchUsedCache) {
                app.startForegroundService(
                    Intent(app, ScreenshotService::class.java)
                        .putExtra("code", ScreenConsent.code).putExtra("data", ScreenConsent.data)
                        .putExtra("mode", "watch").putExtra("receiver", receiver)
                )
            } else {
                launchCapturePrompt("watch", receiver)
            }
        } catch (e: Exception) {
            lastWatchUsedCache = false
            if (lastWatchErr == null) lastWatchErr = e.message ?: "background start blocked"
            try {
                launchCapturePrompt("watch", receiver)
            } catch (_: Exception) {
                done.complete(null)
            }
        }
        return withTimeoutOrNull(120000) { done.await() }
    }

    /** System consent prompt + backup notification (covers background-launch blocks). */
    private fun launchCapturePrompt(mode: String, receiver: ResultReceiver?) {
        val app = getApplication<Application>()
        if (receiver != null) voiceNote = "Approve the screen capture prompt…"
        try {
            app.startActivity(
                Intent(app, ShotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("mode", mode).putExtra("receiver", receiver)
            )
        } catch (_: Exception) {
        }
        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                runCatching {
                    nm.createNotificationChannel(
                        NotificationChannel("jarvis_shot", "Jarvis screenshots", NotificationManager.IMPORTANCE_HIGH)
                    )
                }
            }
            val tap = PendingIntent.getActivity(
                app, 7702,
                Intent(app, ShotActivity::class.java)
                    .putExtra("mode", mode).putExtra("receiver", receiver),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                7702, NotificationCompat.Builder(app, "jarvis_shot")
                    .setSmallIcon(R.drawable.ic_stat_jarvis)
                    .setContentTitle("Jarvis needs one tap")
                    .setContentText("Tap to approve screen capture")
                    .setContentIntent(tap).setAutoCancel(true).build()
            )
        } catch (_: Exception) {
        }
    }

    private fun cancelCaptureNotif() {
        try {
            (getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(7702)
        } catch (_: Exception) {
        }
        voiceNote = null
    }

    fun takeScreenshot() {
        try {
            launchCapturePrompt("share", null)
        } catch (_: Exception) {
            toast("Couldn't open screen capture")
        }
    }

    fun openAccessSettings() {
        try {
            getApplication<Application>().startActivity(
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    private fun needAccess(): String {
        openAccessSettings()
        return "Turn on Jarvis in Accessibility settings first \u2014 opening it now."
    }

    /** Accessibility result, or the right guidance (don't re-open settings if already on). */
    private fun accessOrNeed(call: () -> String?): String {
        call()?.let { return it }
        if (isAccessEnabled(getApplication())) return "Screen service is starting — try again in a moment."
        return needAccess()
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

    private var speakChunks = 1

    private fun stopSpeaking() {
        SpeechState.speaking = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun speakText(t: String) {
        speak(t, force = true)
    }

    fun interruptSpeech() {
        stopSpeaking()
        SpeechState.speaking = false
        HudStateBus.update(speaking = false)
        if (WakeService.isRunning) {
            try {
                getApplication<Application>().startService(
                    Intent(getApplication(), WakeService::class.java).setAction(WakeService.ACTION_HUSH)
                )
            } catch (_: Exception) {
            }
        }
        if (continuous && ttsOn) {
            try {
                startListening()
            } catch (_: Exception) {
            }
        }
    }

    /** Id of the final chunk of the current speak generation. */
    private fun lastUtteranceId(): String = "jarvis:$speakGen:" + (speakChunks - 1)

    private fun speak(text: String, force: Boolean = false, voiceCmd: Boolean = false) {
        if (CallStateBus.current && !force) return // never talk over a phone call
        if ((!ttsOn && !force) || tts == null) {
            // Nothing will be spoken (voice off / no engine) — a voice command in a
            // live session still needs its mic back, since no onDone will fire.
            if (voiceCmd && (continuous || convoActive) && !showSettings) {
                Handler(Looper.getMainLooper()).post { try { startListening() } catch (_: Exception) {} }
            }
            return
        }
        val t = tts ?: return
        try {
            val clean = cleanForSpeech(text)
            if (clean.isEmpty()) return
            val chunks = splitSentences(clean)
            if (chunks.isEmpty()) return
            speakChunks = chunks.size
            speakGen++
            lastSpeakMs = System.currentTimeMillis()
            SpeechState.speaking = true
            HudStateBus.update(speaking = true)
            t.speak(chunks[0], TextToSpeech.QUEUE_FLUSH, null, "jarvis:$speakGen:0")
            chunks.drop(1).forEachIndexed { i, c ->
                t.speak(c, TextToSpeech.QUEUE_ADD, null, "jarvis:$speakGen:" + (i + 1))
            }
        } catch (_: Exception) {
            SpeechState.speaking = false
            HudStateBus.update(speaking = false)
        }
    }

    // ---- voice input (in-app, no Google popup, no beeps) ----

    fun startListening(fromUser: Boolean = false) {
        if (CallStateBus.current) {
            if (fromUser) toast("On a call — voice paused")
            return
        }
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
            utterPeakRms = 0f
            voiceNote = null
            heardFresh = false
            clearStuckSpeech()
            commandAudioBegin()
            stopVpCapture()
            vpCapture = if (guardOn && hasVoiceprint()) VoiceCapture().let { if (it.start()) it else null } else null
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    HudStateBus.update(listening = true)
                    HudStateBus.postTicker("[MIC: LIVE]")
                }

                override fun onResults(results: Bundle?) {
                    if (r !== recognizer) return // stale callback from a replaced session
                    listening = false
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
                    val heard = bestHeard(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                        results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    )
                    if (heard.isNotEmpty()) {
                        lastHeard = heard
                        heardFresh = true
                    }
                    val pcm = stopVpCapture()
                    destroyRecognizer()
                    listenTries = 0
                    convoErrs = 0
                    viewModelScope.launch(Dispatchers.Default) {
                        val verdict = verifyPcm(pcm)
                        withContext(Dispatchers.Main) { handleCommandResult(heard, verdict) }
                    }
                }

                override fun onError(error: Int) {
                    if (r !== recognizer) return // stale callback from a replaced session
                    listening = false
                    HudStateBus.update(listening = false)
                    BubbleLevelBus.reset()
                    stopVpCapture()
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
                    val msg = voiceErrorText(error)
                    if (convoActive) {
                        commandAudioEnd()
                        if (error == SpeechRecognizer.ERROR_CLIENT) {
                            endConvoSession() // user took over / cancelled
                        } else if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            convoErrs = 0
                            if (convoExpired(System.currentTimeMillis(), convoLastVoiceMs)) {
                                endConvoSession()
                            } else {
                                restartConvoListen()
                            }
                        } else if (++convoErrs < CONVO_MAX_CONSEC_ERRORS) {
                            // Transient (network/server/busy) — stay in session, retry.
                            if (msg != null) {
                                voiceNote = msg
                                toast(msg)
                            }
                            restartConvoListen()
                        } else {
                            convoErrs = 0
                            if (msg != null) {
                                voiceNote = msg
                                toast(msg)
                            }
                            endConvoSession()
                        }
                        return
                    }
                    if (msg != null) {
                        voiceNote = msg
                        toast(msg)
                    }
                    commandAudioEnd()
                    resumeWakeService()
                }

                override fun onEndOfSpeech() { BubbleLevelBus.reset() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { BubbleLevelBus.pushRms(rmsdB); if (rmsdB > utterPeakRms) utterPeakRms = rmsdB }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeForListen(hindiListen))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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

    // ---- conversation session (wake once, talk until 10s of silence) ----

    fun startConvoSession() {
        convoActive = true
        convoLastVoiceMs = System.currentTimeMillis()
        HudStateBus.postTicker("[CONVO: LIVE]")
        startConvoWatchdog()
        // Half-duplex: wait for the "Yes sir?" greeting to finish first.
        viewModelScope.launch {
            var waits = 0
            while (SpeechState.speaking && waits < 15 && convoActive) {
                delay(200)
                waits++
            }
            if (convoActive && !listening) startListening()
        }
    }

    private fun restartConvoListen() {
        if (!convoActive) return
        viewModelScope.launch {
            delay(350)
            if (convoActive && !listening && !busy && !SpeechState.speaking && !showSettings) {
                try {
                    startListening()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startConvoWatchdog() {
        convoWatchdog?.cancel()
        convoWatchdog = viewModelScope.launch {
            while (convoActive) {
                delay(1500)
                if (!convoActive) return@launch
                if (listening || busy || SpeechState.speaking) continue
                if (convoExpired(System.currentTimeMillis(), convoLastVoiceMs)) {
                    endConvoSession()
                } else if (!showSettings) {
                    // Idle gap with time left (voice off, missed callback) — reopen mic.
                    try {
                        startListening()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun endConvoSession() {
        if (!convoActive) return
        convoActive = false
        convoWatchdog?.cancel()
        convoWatchdog = null
        stopVpCapture()
        destroyRecognizer()
        listening = false
        HudStateBus.update(listening = false)
        BubbleLevelBus.reset()
        commandAudioEnd()
        HudStateBus.postTicker("[CONVO: END]")
        resumeWakeService()
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        stopVpCapture()
        destroyRecognizer()
        listening = false
        HudStateBus.update(listening = false)
        BubbleLevelBus.reset()
        commandAudioEnd()
        if (!convoActive) resumeWakeService()
    }

    /** Drop a stale speaking flag (kills runaway TTS first) so the mic can reopen. */
    private fun clearStuckSpeech() {
        if (!speakingStuck(SpeechState.speaking, System.currentTimeMillis(), lastSpeakMs)) return
        try { tts?.stop() } catch (_: Exception) { }
        SpeechState.speaking = false
        HudStateBus.update(speaking = false)
        HudStateBus.postTicker("[SPEECH: RESET]")
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
        MicHandoff.appActive = true
    }

    private fun commandAudioEnd() {
        muteBeeps(false)
        MicHandoff.appActive = false
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
        } catch (_: Exception) { false }
    }

    /**
     * Ask Android to drop Jarvis from battery optimization. The UI owns the
     * result contract (see JarvisScreen), so this just raises the request.
     */
    fun requestBatteryUnrestricted() {
        batteryFixTick++
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
            armStandbyWatchdog(appCtx, true)
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
            armStandbyWatchdog(appCtx, false)
        }
        StarkWidgetProvider.refreshAll(appCtx)
        refreshReactorWidgets(appCtx)
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
            if (!WakeService.isRunning && store.wakeEnabled) {
                // Service died (OEM kill) while armed — revive it, not just resume.
                appCtx.startForegroundService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_START))
            } else {
                appCtx.startService(Intent(appCtx, WakeService::class.java).setAction(WakeService.ACTION_RESUME))
            }
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
        for ((r, t, ts, img) in c.msgs) {
            messages.add(ChatMessage(if (r == "user") "user" else "bot", t, ts, if (img.isEmpty()) null else img))
        }
        if (messages.isEmpty()) messages.add(ChatMessage("bot", greet()))
        showChats = false
        persist()
    }

    fun clearChats() {
        stopSpeaking()
        chats.clear()
        chats.add(ChatData("c" + System.currentTimeMillis(), "New chat", mutableListOf()))
        activeChatId = chats[0].id
        messages.clear()
        store.saveChats(chats)
    }

    fun renameChat(id: String, title: String) {
        val i = chats.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = chats[i]
        chats[i] = ChatData(c.id, cleanTitle(title), c.msgs)
        store.saveChats(chats)
    }

    /** Refresh Stark dashboard telemetry (temp + ping, best-effort). */
    fun refreshDashboard() {
        dashBatt = try {
            StarkDeviceController(getApplication<Application>()).getBatteryLevel()
        } catch (_: Exception) {
            -1
        }
        viewModelScope.launch {
            dashTemp = fetchDashTemp()
            dashPing = measurePing()
        }
    }

    private suspend fun fetchDashTemp(): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://wttr.in/?format=%25t").get().build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext "\u2014"
                r.body?.string().orEmpty().trim().ifEmpty { "\u2014" }
            }
        } catch (_: Exception) {
            "\u2014"
        }
    }

    private suspend fun measurePing(): String = withContext(Dispatchers.IO) {
        try {
            val t0 = System.nanoTime()
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("8.8.8.8", 53), 3000)
            }
            ((System.nanoTime() - t0) / 1000000).toString() + "ms"
        } catch (_: Exception) {
            "\u2014"
        }
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
            for ((r, t, ts, img) in chats[0].msgs) {
                messages.add(ChatMessage(if (r == "user") "user" else "bot", t, ts, if (img.isEmpty()) null else img))
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
    }

    var githubToken: String = loadGithubToken(getApplication())

    fun saveGithubToken(t: String) {
        githubToken = t.trim().take(200)
        saveGithubToken(getApplication(), githubToken)
        toast(if (githubToken.isEmpty()) "GitHub token cleared" else "GitHub token saved")
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

    // ---- voiceprint (text-dependent speaker check, fully offline) ----

    private fun stopVpCapture(): ShortArray? {
        val c = vpCapture
        vpCapture = null
        return try {
            c?.stop()
        } catch (_: Exception) {
            null
        }
    }

    private fun voiceprintTemplates(): List<List<FloatArray>>? {
        vpCache?.let { return it }
        if (store.vp_templates.isBlank() || store.vp_base <= 0f) return null
        val t = templatesFromString(store.vp_templates)
        if (t == null || t.size != 3) return null
        vpCache = t
        return t
    }

    private fun hasVoiceprint(): Boolean = voiceprintTemplates() != null

    private fun verifyPcm(pcm: ShortArray?): VpVerdict {
        if (pcm == null || !guardOn) return VpVerdict.UNKNOWN
        val templates = voiceprintTemplates() ?: return VpVerdict.UNKNOWN
        return try {
            verifyVoiceprint(pcm, templates, vpThresholdFor(store.vp_base, store.vp_mult))
        } catch (_: Exception) {
            VpVerdict.UNKNOWN
        }
    }

    /** Continue onResults on main once the voiceprint verdict is in. */
    private fun handleCommandResult(heard: String, verdict: VpVerdict) {
        if (convoActive) {
            commandAudioEnd()
            if (heard.isNotEmpty() && !isNearbyVoice(utterPeakRms)) {
                // Far-field/background chatter — ignore, stay in session.
                HudStateBus.postTicker("[NOISE: IGNORED]")
                restartConvoListen()
                return
            }
            if (heard.isNotEmpty() && sendGated(heard, verdict)) {
                convoLastVoiceMs = System.currentTimeMillis()
            } else {
                restartConvoListen()
            }
            return // session owns the mic until the 10s timeout
        }
        commandAudioEnd()
        if (heard.isNotEmpty()) sendGated(heard, verdict)
        resumeWakeService()
    }

    /** Voice-gated send: voiceprint first, name fallback, typed fallback. */
    private fun sendGated(heard: String, verdict: VpVerdict): Boolean {
        val d = voiceGateDecision(heard, guardOn, isDeviceLocked(), store.masterName, hasVoiceprint(), verdict)
        if (!d.send) {
            if (d.note != null) {
                voiceNote = d.note
                HudStateBus.postTicker("[VOICE: REJECTED]")
            }
            return false
        }
        return send(d.cleaned, fromVoice = true, idChecked = d.bypassGuard)
    }

    private fun enrollVoiceprint(): String {
        val name = store.masterName.trim()
        if (name.isEmpty()) return "Install your Master Key first — I need your name for the phrase."
        if (enrollingVp) return "Enrollment already running."
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.RECORD_AUDIO
            return "I need mic permission to enroll — allow it, then say that again."
        }
        if (convoActive) endConvoSession() else stopListening()
        enrollingVp = true
        pauseWakeService()
        MicHandoff.appActive = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val phrase = "Jarvis, it's $name"
                val takes = mutableListOf<List<FloatArray>>()
                for (i in 1..3) {
                    withContext(Dispatchers.Main) {
                        voiceNote = "Enrollment $i of 3: say \u201c$phrase\u201d"
                        speak("Say: $phrase", force = true)
                    }
                    var waits = 0
                    while (SpeechState.speaking && waits < 40) {
                        delay(200)
                        waits++
                    }
                    delay(400)
                    val pcm = VoiceCapture.recordFixedMs(3200) ?: break
                    val mf = withContext(Dispatchers.Default) { mfccOfTake(pcm) }
                    if (mf == null) {
                        withContext(Dispatchers.Main) {
                            voiceNote = "Too quiet — enrollment stopped. Try again in a quiet room."
                        }
                        return@launch
                    }
                    takes.add(mf)
                    withContext(Dispatchers.Main) { HudStateBus.postTicker("[VOICEPRINT: TAKE $i/3]") }
                    delay(700)
                }
                if (takes.size != 3) {
                    withContext(Dispatchers.Main) { voiceNote = "Enrollment failed — couldn't capture audio." }
                    return@launch
                }
                val spread = withContext(Dispatchers.Default) {
                    maxOf(dtwDistance(takes[0], takes[1]), dtwDistance(takes[0], takes[2]), dtwDistance(takes[1], takes[2]))
                }
                withContext(Dispatchers.Main) {
                    if (spread > VP_ENROLL_MAX_SPREAD) {
                        voiceNote = "Takes differed too much — say the same phrase 3 times."
                    } else {
                        store.vp_templates = templatesToString(takes)
                        store.vp_base = spread.toFloat()
                        store.vp_phrase = phrase
                        vpCache = null
                        voiceNote = "Voiceprint saved."
                        speak("Voiceprint saved. Only your voice will command me now.", force = true)
                        HudStateBus.postTicker("[VOICEPRINT: SAVED]")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    enrollingVp = false
                    MicHandoff.appActive = false
                    resumeWakeService()
                }
            }
        }
        return "Starting enrollment — say the phrase 3 times."
    }

    private fun removeVoiceprint(): String {
        store.vp_templates = ""
        store.vp_base = -1f
        store.vp_phrase = ""
        vpCache = null
        return "Voiceprint removed — name check only from now on."
    }

    private fun setVpSensitivity(mult: Float, label: String): String {
        store.vp_mult = mult
        return if (hasVoiceprint()) "Voiceprint sensitivity: $label." else "Sensitivity set to $label (enroll your voice first)."
    }

    private fun vpStatus(): String {
        val g = if (guardOn) "Voice guard on" else "Voice guard off"
        if (!hasVoiceprint()) return "$g. No voiceprint — say \u201cenroll my voice\u201d to add one."
        return "$g. Voiceprint enrolled (\u201c${store.vp_phrase}\u201d). Only your voice commands me" +
            (if (isDeviceLocked()) " — your name works as backup." else ".")
    }

    /** Master voice guard: automatic once a Master Key is installed (toggle by voice). */
    private val guardOn: Boolean get() = masterInstalled && voiceGuard

    private fun isDeviceLocked(): Boolean = try {
        val km = getApplication<Application>().getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        km.isKeyguardLocked
    } catch (_: Exception) {
        false
    }

    fun send(raw: String, fromVoice: Boolean = false, idChecked: Boolean = false): Boolean {
        var text = raw.trim()
        if (fromVoice && guardOn && !idChecked) {
            val g = guardCommand(text, guardOn = true, locked = isDeviceLocked(), masterName = store.masterName)
            if (!g.allowed) {
                voiceNote = "Master voice guard: include your name"
                HudStateBus.postTicker("[GUARD: NEED NAME]")
                return false
            }
            text = g.cleaned
        }
        val sendChatId = activeChatId
        if (text.isEmpty()) return false
        if (busy) { toast("Still thinking — one sec"); return false }
        messages.add(ChatMessage("user", text))
        persist()
        HudStateBus.update(online = brainOk)
        Router.detect(text)?.let { hit ->
            if (hit.tool == "weather" || hit.tool == "fx" || hit.tool.startsWith("github") || hit.tool == "screen_watch") {
                busy = true
                HudStateBus.update(thinking = true)
                viewModelScope.launch {
                    try {
                        val reply = if (hit.tool == "fx") fetchFx(hit.arg)
                        else if (hit.tool.startsWith("github")) fetchGithub(hit)
                        else if (hit.tool == "screen_watch") watchScreen(hit.arg)
                        else fetchWeather(hit.arg)
                        deliverReply(sendChatId, reply)
                        if (fromVoice) speak(reply, voiceCmd = true)
                    } catch (e: Exception) {
                        deliverReply(sendChatId, "⚠️ " + if (hit.tool == "fx") "Couldn't fetch rates." else if (hit.tool == "screen_watch") "Couldn't watch the screen." else "Couldn't reach the weather service.")
                    } finally {
                        busy = false
                        HudStateBus.update(thinking = false)
                        persist()
                    }
                }
                return true
            }
            if (hit.tool == "gen_image") {
                busy = true
                HudStateBus.update(thinking = true)
                voiceNote = "Painting your image…"
                viewModelScope.launch {
                    try {
                        val (caption, path) = generateImageFull(hit.arg)
                        deliverImageReply(sendChatId, caption, path)
                        if (fromVoice) speak(caption, voiceCmd = true)
                    } catch (e: Exception) {
                        deliverReply(sendChatId, "\u26a0\ufe0f " + (e.message?.take(200) ?: "Couldn't generate the image."))
                    } finally {
                        busy = false
                        HudStateBus.update(thinking = false)
                        voiceNote = null
                        persist()
                    }
                }
                return true
            }
            val reply = runTool(hit)
            messages.add(ChatMessage("bot", reply))
            if (fromVoice) speak(reply, voiceCmd = true)
            persist()
            return true
        }
        matchHook(text, store.loadHooks())?.let { hook ->
            busy = true
            HudStateBus.update(thinking = true)
            viewModelScope.launch {
                try {
                    val reply = fireHook(hook)
                    deliverReply(sendChatId, reply)
                    if (fromVoice) speak(reply, voiceCmd = true)
                } catch (e: Exception) {
                    deliverReply(sendChatId, "⚠️ " + hook.name + " failed: " + e.message?.take(120))
                } finally {
                    busy = false
                    HudStateBus.update(thinking = false)
                    persist()
                }
            }
            return true
        }
        if (!brainOk) {
            val reply = if (builtinKey.isNotBlank()) "🔑 Install your Master Key to unlock the brain — or add your own Gemini key in Settings ⚙️. Offline I can still do time, calculations, memory, reminders, device control, todos, and notes."
            else "🔑 I need a Gemini API key for that (free from aistudio.google.com — add it in Settings ⚙️). Offline I can still do time, calculations, memory, reminders, device control, todos, and notes."
            messages.add(ChatMessage("bot", reply))
            if (fromVoice) speak(reply, voiceCmd = true)
            persist()
            return true
        }
        busy = true
        HudStateBus.update(thinking = true)
        HudStateBus.postTicker(if (masterInstalled) "[MASTER UPLINK]" else "[UPLINK: GEMINI]")
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val system = buildSystem(store.facts())
                val hist = messages.dropLast(1).takeLast(40)
                    .map { (if (it.role == "user") "user" else "model") to it.text }
                val (reply, _) = try {
                    GeminiApi.chat(effectiveKey, resolveModels(false), system, hist, text)
                } catch (e: GeminiApi.JarvisError) {
                    if (!e.message.orEmpty().contains("404")) throw e
                    // Model list went stale — rediscover once and retry.
                    GeminiApi.chat(effectiveKey, resolveModels(true), system, hist, text)
                }
                deliverReply(sendChatId, reply)
                if (fromVoice) speak(reply, voiceCmd = true)
                HudStateBus.postTicker("[UPLINK: " + (System.currentTimeMillis() - t0) + "ms]")
            } catch (e: Exception) {
                deliverReply(sendChatId, "⚠️ ${e.message}")
            } finally {
                busy = false
                HudStateBus.update(thinking = false)
                persist()
            }
        }
        return true
    }

    /** Route an async reply to the chat it was sent from (user may have switched). */
    /** Generate, save (files + gallery), return caption + filesDir path. */
    private suspend fun generateImageFull(arg: String): Pair<String, String?> {
        if (!brainOk) throw GeminiApi.JarvisError("I need a Gemini API key for image generation (free from aistudio.google.com — add it in Settings \u2699\ufe0f).")
        val models = genImageModels(resolveModels(false))
        val (mime, bytes) = GeminiApi.generateImage(effectiveKey, models, arg)
        val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
        val dir = java.io.File(getApplication<Application>().filesDir, "gen").apply { mkdirs() }
        val f = java.io.File(dir, "img-" + System.currentTimeMillis() + "." + ext)
        withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        val gallery = saveImageToGallery(bytes, f.name, mime)
        val caption = "Here's what I painted" + (if (gallery) " — saved to your gallery." else ".")
        return caption to f.absolutePath
    }

    private fun deliverImageReply(sendChatId: String, caption: String, path: String?) {
        if (activeChatId == sendChatId) {
            messages.add(ChatMessage("bot", caption, imagePath = path))
        } else {
            chats.find { it.id == sendChatId }?.let {
                it.msgs.add(StoredMsg("model", caption, System.currentTimeMillis(), path ?: ""))
                store.saveChats(chats)
            }
        }
    }

    /** Best-effort copy into Pictures/Jarvis. Never throws. */
    private fun saveImageToGallery(bytes: ByteArray, name: String, mime: String): Boolean {
        return try {
            val app = getApplication<Application>()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jarvis")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                app.contentResolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deliverReply(sendChatId: String, reply: String) {
        if (activeChatId == sendChatId) {
            messages.add(ChatMessage("bot", reply))
        } else {
            chats.find { it.id == sendChatId }?.let {
                it.msgs.add(StoredMsg("model", reply, System.currentTimeMillis()))
                store.saveChats(chats)
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

    private suspend fun fetchFx(arg: String): String = withContext(Dispatchers.IO) {
        val (v, from, to) = parseCurrency(arg) ?: throw IllegalStateException("bad fx request")
        val req = Request.Builder()
            .url("https://api.frankfurter.app/latest?from=$from&to=$to").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("fx HTTP " + resp.code)
            val rate = parseFxRate(resp.body?.string().orEmpty(), to)
                ?: throw IllegalStateException("bad fx data")
            formatFx(v, from, rate, to)
        }
    }

    private suspend fun fetchWeather(arg: String): String = withContext(Dispatchers.IO) {
        val city = parseWeatherCity(arg)
        val url = if (city.isBlank()) "https://wttr.in/?format=j1"
        else "https://wttr.in/" + Uri.encode(city) + "?format=j1"
        val req = Request.Builder().url(url).header("User-Agent", "curl/8.0").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("weather HTTP " + resp.code)
            val w = parseWttr(resp.body?.string().orEmpty())
                ?: throw IllegalStateException("bad weather data")
            formatWeather(w)
        }
    }

    private fun readNotifs(): String {
        val ctx = getApplication<Application>()
        return try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
            if (!enabled.contains(ctx.packageName)) {
                val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                "One tap — enable Jarvis in notification access, then ask again."
            } else {
                formatNotifs(NotifReader.snapshot())
            }
        } catch (_: Exception) { "Couldn't read notifications." }
    }

    private fun morningRoutine(): String {
        val now = java.time.LocalTime.now()
        val time = now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        val b = try { collectBriefing() } catch (_: Exception) { null }
        val batt = if (b != null && b.batteryPct >= 0) " Battery at ${b.batteryPct}%." else ""
        return "${daypart(now.hour)}! It's $time.$batt How can I help?"
    }

    private fun setAlarm(time: Pair<Int, Int>?): String {
        return try {
            val ctx = getApplication<Application>()
            if (time == null) {
                val i = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                "Opening your alarms — tell me a time like “wake me at 7 am” to set one."
            } else {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, time.first)
                    .putExtra(AlarmClock.EXTRA_MINUTES, time.second)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis alarm")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                "Alarm set for %02d:%02d.".format(time.first, time.second)
            }
        } catch (_: Exception) { "Couldn't open the clock app." }
    }

    private fun setTimer(seconds: Int): String {
        return try {
            val ctx = getApplication<Application>()
            val i = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis timer")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Timer set for ${fmtDur(seconds)}."
        } catch (_: Exception) { "Couldn't open the clock app." }
    }

    private fun navigateTo(q: String): String {
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(i)
            "Navigating to $q."
        } catch (_: Exception) { "Couldn't open Maps." }
    }

    private fun webSearch(q: String): String {
        return try {
            val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(i)
            "Searching for $q."
        } catch (_: Exception) { "Couldn't start a search." }
    }

    private fun playMedia(q: String): String {
        return try {
            val clean = q.replace(
                Regex("""\s+on\s+(youtube|spotify|jiosaavn|wynk)$""", RegexOption.IGNORE_CASE), ""
            ).trim()
            val label = clean.ifBlank { "music" }
            val i = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(label))
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(i)
            "Playing $label."
        } catch (_: Exception) { "Couldn't open YouTube." }
    }

    private fun runDevice(arg: String): String {
        val cmd = parseDeviceCommand(arg)
            ?: return "I can place and answer calls, text, open apps and chats, or flip the torch — e.g. “text mom I’ll be late”."
        return when (cmd) {
            is OpenApp -> openAppByName(cmd.name)
            is Silence -> setSilence(true)
            is Unsilence -> setSilence(false)
            is Torch -> setTorch(cmd.on)
            is CallContact -> callContact(cmd.query)
            is TextMessage -> textMessage(cmd.app, cmd.contact, cmd.body)
            is OpenChat -> openChat(cmd.app, cmd.contact)
            is AnswerCall -> answerCall()
            is EndCall -> endCall()
            is Speaker -> setCallSpeaker(cmd.on)
            is WifiPanel -> openWifiPanel()
            is SysSettings -> openSysSettings()
            is SetAlarm -> setAlarm(cmd.time)
            is SetTimer -> setTimer(cmd.seconds)
            is NavigateTo -> navigateTo(cmd.query)
            is WebSearch -> webSearch(cmd.query)
            is PlayMedia -> playMedia(cmd.query)
        }
    }

    private fun openAppByName(name: String): String {
        directLaunchApp(name)?.let { return it }
        // Not launchable (e.g. a website shortcut with no app entry) — try recents.
        return openAppFromRecents(name)
    }

    /** Launch by label. Null when nothing launchable matches. */
    private fun directLaunchApp(name: String): String? {
        val ctx = getApplication<Application>()
        data class Cand(val label: String, val pkg: String, val open: () -> Boolean)
        val cands = mutableListOf<Cand>()
        try {
            // Tier 1: LauncherApps sees everything the system launcher shows (incl. web apps).
            val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
            for (a in la.getActivityList(null, android.os.Process.myUserHandle())) {
                val label = a.label?.toString().orEmpty()
                val comp = a.componentName
                cands.add(
                    Cand(label, comp.packageName, open = fun(): Boolean {
                        return try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_MAIN).setClassName(comp.packageName, comp.className)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            true
                        } catch (_: Exception) { false }
                    })
                )
            }
        } catch (_: Exception) { }
        try {
            // Tier 2: package-manager queries (need the LAUNCHER <queries> entry).
            val pm = ctx.packageManager
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).ifEmpty { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), 0) }
            for (r in apps) {
                val label = r.loadLabel(pm)?.toString().orEmpty()
                val pkg = r.activityInfo.packageName
                if (cands.none { it.pkg == pkg }) {
                    cands.add(
                        Cand(label, pkg, open = fun(): Boolean {
                            return try {
                                val li = pm.getLaunchIntentForPackage(pkg) ?: return false
                                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(li)
                                true
                            } catch (_: Exception) { false }
                        })
                    )
                }
            }
        } catch (_: Exception) { }
        val hit = cands.firstOrNull { isAppMatchStrict(it.label, it.pkg, name) }
            ?: cands.firstOrNull { isAppMatchLoose(it.label, name) }
            ?: return null
        return if (hit.open()) "Opening ${hit.label}." else null
    }

    /** Recents fallback: open the task switcher and tap the matching card. */
    private fun openAppFromRecents(name: String): String {
        if (!isAccessEnabled(getApplication())) return "I couldn't find an app called “$name”."
        if (!AccessBridge.recents()) return "I couldn't find an app called “$name”."
        viewModelScope.launch {
            delay(900)
            try { AccessBridge.tap(name) } catch (_: Exception) { }
        }
        return "“$name” isn't installed as an app — I opened your recent apps to tap it."
    }

    private fun openRecentsScreen(): String {
        if (!isAccessEnabled(getApplication())) return needAccess()
        return if (AccessBridge.recents()) "Recent apps — tap one, or say “open X from recents”."
        else "Couldn't open recent apps."
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

    private sealed interface Recipient {
        data class Found(val number: String) : Recipient
        object NeedPermission : Recipient
        object NotFound : Recipient
    }

    /** Digits fast-path, else contact lookup (queues READ_CONTACTS when missing). */
    private fun resolveRecipient(query: String): Recipient {
        val ctx = getApplication<Application>()
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.length >= 7 && digits.length >= query.trim().length - 2) {
            return Recipient.Found(digits)
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.READ_CONTACTS
            return Recipient.NeedPermission
        }
        val n = findContactNumber(ctx, query)
        return if (n != null) Recipient.Found(n) else Recipient.NotFound
    }

    private fun callContact(query: String): String {
        val ctx = getApplication<Application>()
        val number = when (val r = resolveRecipient(query)) {
            is Recipient.Found -> r.number
            Recipient.NeedPermission -> return "I need contacts permission to find “$query” — allow it, then ask again."
            Recipient.NotFound -> return "Couldn’t find “$query” in contacts."
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.CALL_PHONE
            return "I need call permission to dial directly — allow it, then say that again."
        }
        return try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Calling $query."
        } catch (_: Exception) {
            "Couldn’t place the call."
        }
    }

    private fun telephonyCallState(ctx: Context): Int? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            @Suppress("DEPRECATION")
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            tm.callState
        } catch (_: Exception) {
            null
        }
    }

    private fun answerCall(): String {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.ANSWER_PHONE_CALLS
            return "I need phone permission to answer calls — allow it, then say that again."
        }
        val st = telephonyCallState(ctx)
        if (st != null && st != android.telephony.TelephonyManager.CALL_STATE_RINGING) {
            return if (st == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK) "You're already on a call."
            else "No incoming call right now."
        }
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            tm.acceptRingingCall()
            "Answered."
        } catch (_: Exception) {
            "Couldn't answer that call."
        }
    }

    private fun endCall(): String {
        val ctx = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.ANSWER_PHONE_CALLS
            return "I need phone permission to end calls — allow it, then say that again."
        }
        if (telephonyCallState(ctx) == android.telephony.TelephonyManager.CALL_STATE_IDLE) {
            return "No active call."
        }
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            tm.endCall()
            "Call ended."
        } catch (_: Exception) {
            "Couldn't end the call."
        }
    }

    private fun setCallSpeaker(on: Boolean): String {
        val ctx = getApplication<Application>()
        if (telephonyCallState(ctx) == android.telephony.TelephonyManager.CALL_STATE_IDLE) {
            return "No active call — nothing to put on speaker."
        }
        return try {
            audio.setSpeakerphoneOn(on)
            val ok = try {
                audio.isSpeakerphoneOn == on
            } catch (_: Exception) {
                true
            }
            if (!ok) return "Couldn't flip the speaker."
            if (on) "Speaker on." else "Speaker off."
        } catch (_: Exception) {
            "Couldn't flip the speaker."
        }
    }

    private fun textMessage(app: MsgApp, contact: String, body: String): String {
        val ctx = getApplication<Application>()
        if (app == MsgApp.TELEGRAM && contact.isEmpty()) {
            return if (openTelegramShare(ctx, body)) "Pick a Telegram chat to send that."
            else "Couldn’t open Telegram sharing."
        }
        val number = when (val r = resolveRecipient(contact)) {
            is Recipient.Found -> r.number
            Recipient.NeedPermission -> return "I need contacts permission to find “$contact” — allow it, then ask again."
            Recipient.NotFound -> return "Couldn’t find “$contact” in contacts."
        }
        return when (app) {
            MsgApp.SMS -> sendSmsText(ctx, contact, number, body)
            MsgApp.WHATSAPP -> openWhatsAppChat(ctx, contact, number, body)
            MsgApp.TELEGRAM -> if (openTelegramShare(ctx, body)) {
                "Telegram needs a username — pick $contact’s chat to send that."
            } else {
                "Couldn’t open Telegram sharing."
            }
        }
    }

    private fun sendSmsText(ctx: Context, label: String, number: String, body: String): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permRequest = Manifest.permission.SEND_SMS
            return "I need SMS permission to text directly — allow it, then say that again."
        }
        return if (sendSmsNow(ctx, number, body)) {
            "Texted $label: “${body.take(80)}”"
        } else {
            "Couldn’t send that text."
        }
    }

    private fun openWhatsAppChat(ctx: Context, label: String, number: String, body: String?): String {
        return try {
            val digits = waDigits(number, java.util.Locale.getDefault().country ?: "")
            val url = "https://wa.me/$digits" + if (body != null) "?text=" + Uri.encode(body) else ""
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                i.setPackage("com.whatsapp")
                ctx.startActivity(i)
            } catch (_: Exception) {
                i.setPackage(null)
                ctx.startActivity(i)
            }
            if (body != null) "Opening $label’s WhatsApp chat — tap send." else "Opening $label’s WhatsApp chat."
        } catch (_: Exception) {
            "Couldn’t open WhatsApp."
        }
    }

    // ——— Scheduled messaging ———
    private fun scheduleSchedMsg(req: SchedMsgRequest?): String {
        if (req == null) return "Say it like: “text mom I'll be late tomorrow at 9am”."
        if (req.app == MsgApp.TELEGRAM) {
            return "Telegram can't auto-send to a contact — only SMS and WhatsApp can. Try “text …” or “whatsapp …”."
        }
        val ctx = getApplication<Application>()
        val number = when (val r = resolveRecipient(req.contact)) {
            is Recipient.Found -> r.number
            Recipient.NeedPermission -> return "I need contacts permission to find “${req.contact}” — allow it, then ask again."
            Recipient.NotFound -> return "Couldn't find “${req.contact}” in contacts."
        }
        if (req.app == MsgApp.SMS && ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permRequest = Manifest.permission.SEND_SMS
            return "I need SMS permission to send automatically — allow it, then ask again."
        }
        val now = System.currentTimeMillis()
        val at = when (val w = req.whenAt) {
            is InMinutes -> now + w.minutes * 60_000L
            is AtTime -> atToMillis(w.hour, w.minute, w.tomorrow)
        }
        if (at <= now) return "That time already passed — pick a future time."
        val id = store.nextSchedMsgId()
        val app = if (req.app == MsgApp.WHATSAPP) "wa" else "sms"
        val list = store.loadSchedMsgs()
        list.add(SchedMsgItem(id, at, app, req.contact, number, req.body))
        store.saveSchedMsgs(list)
        runCatching { armSchedMsgAlarm(ctx, id, at, app, req.contact, number, req.body) }
        val auto = if (app == "wa" && !isAccessEnabled(ctx)) {
            " Turn on Accessibility for fully automatic sending — otherwise I'll open the chat so you can tap send."
        } else ""
        val kind = if (app == "wa") "WhatsApp to" else "text to"
        return "Scheduled $kind ${req.contact} ${dueText(at, now)}: “${req.body.take(80)}”.$auto"
    }

    private fun listSchedMsgs(): String {
        val now = System.currentTimeMillis()
        val items = store.loadSchedMsgs().filter { it.at > now }.sortedBy { it.at }
        store.saveSchedMsgs(items)
        if (items.isEmpty()) return "No scheduled messages."
        return "Scheduled messages:\n" + items.mapIndexed { i, m ->
            val kind = if (m.app == "wa") "WA" else "SMS"
            "${i + 1}. [$kind → ${m.label}] ${dueText(m.at, now)}: “${m.body.take(60)}”"
        }.joinToString("\n") + "\nSay “cancel scheduled message N” to drop one."
    }

    private fun cancelSchedMsg(arg: String): String {
        val n = arg.trim().toIntOrNull()
            ?: return "Say “cancel scheduled message N” — see numbers in “my scheduled messages”."
        val items = store.loadSchedMsgs().filter { it.at > System.currentTimeMillis() }.sortedBy { it.at }
        val m = items.getOrNull(n - 1) ?: return "No scheduled message #$n."
        store.removeSchedMsg(m.id)
        runCatching { cancelSchedMsgAlarm(getApplication(), m.id) }
        return "Cancelled the scheduled message to ${m.label}."
    }

    private fun openTelegramShare(ctx: Context, body: String): Boolean {
        return try {
            val i = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://t.me/share/url?url=&text=" + Uri.encode(body))
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openChat(app: MsgApp?, contact: String): String {
        val ctx = getApplication<Application>()
        if (app == MsgApp.TELEGRAM) {
            return "I can’t open Telegram chats by contact name yet — try WhatsApp or text."
        }
        val number = when (val r = resolveRecipient(contact)) {
            is Recipient.Found -> r.number
            Recipient.NeedPermission -> return "I need contacts permission to find “$contact” — allow it, then ask again."
            Recipient.NotFound -> return "Couldn’t find “$contact” in contacts."
        }
        if (app == MsgApp.WHATSAPP || (app == null && isAppInstalled(ctx, "com.whatsapp"))) {
            return openWhatsAppChat(ctx, contact, number, null)
        }
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + Uri.encode(number)))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            "Opening $contact’s texts."
        } catch (_: Exception) {
            "Couldn’t open that conversation."
        }
    }

    private fun isAppInstalled(ctx: Context, pkg: String): Boolean {
        return try {
            ctx.packageManager.getLaunchIntentForPackage(pkg) != null
        } catch (_: Exception) {
            false
        }
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

    private var repoCacheAt = 0L
    private var repoCache: List<RepoInfo> = emptyList()

    private suspend fun resolveGithubRepo(token: String, query: String): String? {
        if ("/" in query) return query
        val now = System.currentTimeMillis()
        if (now - repoCacheAt > 10 * 60 * 1000 || repoCache.isEmpty()) {
            repoCache = GithubApi.listRepos(token)
            repoCacheAt = now
        }
        return resolveMatch(repoCache.map { it.full }, query)
    }

    private suspend fun fetchGithub(hit: Router.Hit): String {
        val tok = effectiveGithubToken
        if (tok.isBlank()) return "Add your GitHub token in Settings first \u2014 a fine-grained, read-only token is enough."
        return try {
            when (hit.tool) {
                "github_repos" -> {
                    repoCache = GithubApi.listRepos(tok)
                    repoCacheAt = System.currentTimeMillis()
                    formatRepoList(repoCache)
                }
                "github_repo" -> {
                    val full = resolveGithubRepo(tok, hit.arg)
                        ?: return "I couldn't find a repo matching '${hit.arg}'. Say 'my repos' to see them."
                    formatRepoBrief(GithubApi.repoBrief(tok, full))
                }
                "github_builds" -> {
                    if (hit.arg.isBlank()) return "Which repo? Say 'check builds for ...' or 'my repos' first."
                    val full = resolveGithubRepo(tok, hit.arg)
                        ?: return "I couldn't find a repo matching '${hit.arg}'. Say 'my repos' to see them."
                    formatRuns(full, GithubApi.runs(tok, full))
                }
                "github_issues" -> {
                    val full = resolveGithubRepo(tok, hit.arg)
                        ?: return "I couldn't find a repo matching '${hit.arg}'. Say 'my repos' to see them."
                    formatIssues(full, GithubApi.issues(tok, full))
                }
                "github_read" -> {
                    val q = hit.arg.substringAfter("|")
                    val full = resolveGithubRepo(tok, q)
                        ?: return "I couldn't find a repo matching '$q'. Say 'my repos' to see them."
                    val (text, trunc) = GithubApi.fileText(tok, full, hit.arg.substringBefore("|"))
                    formatFile(full, hit.arg.substringBefore("|"), text, trunc)
                }
                else -> "?"
            }
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            when {
                m.contains("401") -> "GitHub rejected the token \u2014 check it in Settings."
                m.contains("404") -> "Not found on GitHub \u2014 check the name."
                m.contains("too large") -> "That file is too large to read here."
                m.contains("not a file") -> "That's not a file \u2014 try a file path."
                else -> "GitHub said no: " + m.take(120)
            }
        }
    }

    private val voiceHelp = "You can say:\n" +
        "\u2022 \"Briefing\" \u2014 the day at a glance\n" +
        "\u2022 \"Remind me in 10 minutes to stretch\"\n" +
        "\u2022 \"Remember that ...\" / \"Recall ...\"\n" +
        "\u2022 \"Turn on the flashlight\" / \"Call mom\"\n" +
        "\u2022 \"Hands-free on\" / \"Daily briefing on\"\n" +
        "\u2022 \"Smart actions\" / \"List smart actions\"\n" +
        "\u2022 \"Back up my data\" / \"Battery status\"\n" +
        "\u2022 \"What\u0027s new\" / \"Help\"\n" +
        "\u2022 \"What\u0027s on my screen\" / \"Tap ...\"\n" +
        "\u2022 \"Generate an image of ...\""

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
        "coin" -> coinFace(kotlin.random.Random.nextBoolean())
        "dice" -> {
            val sides = parseDice(hit.arg)
            "= ${rollDie(sides, kotlin.random.Random.nextDouble())} (d$sides)"
        }
        "convert" -> convertUnits(hit.arg) ?: "Couldn't convert that."
        "joke" -> jokeAt(kotlin.random.Random.nextInt(1000))
        "routine" -> morningRoutine()
        "device" -> runDevice(hit.arg)
        "notifs" -> readNotifs()
        "lists" -> runLists(hit.arg)
        "handsfree" -> {
            val a = hit.arg.lowercase()
            val on = Regex("""\bon\b|enable""").containsMatchIn(a)
            val off = Regex("""\boff\b|disable""").containsMatchIn(a)
            val target = if (on != off) on else !continuous
            if (target != continuous) toggleContinuous()
            "Hands-free is now ${if (target) "on" else "off"}."
        }
        "briefing" -> formatBriefing(collectBriefing())
            .joinToString("\n") { (k, v) -> "$k: $v" }
        "dailybrief" -> {
            val a = hit.arg.lowercase()
            val on = Regex("""\bon\b|enable""").containsMatchIn(a)
            val off = Regex("""\boff\b|disable""").containsMatchIn(a)
            val target = if (on != off) on else !dailyBriefing
            if (target != dailyBriefing) toggleDailyBriefing()
            "Daily briefing is now ${if (target) "on" else "off"}."
        }
        "hooks" -> {
            val a = hit.arg.lowercase()
            when {
                a.contains("list") || a.contains("show") -> {
                    val hs = hooks()
                    if (hs.isEmpty()) "No smart actions yet. Say 'smart actions' to add one."
                    else "Smart actions:\n" + hs.joinToString("\n") { "\u2022 " + it.name }
                }
                a.contains("remove") || a.contains("delete") -> {
                    val name = Regex("""(?:remove|delete)(?: smart action)?\s+(.+)""")
                        .find(a)?.groupValues?.get(1)?.trim().orEmpty()
                    if (name.isEmpty()) "Which smart action should I remove?"
                    else if (hooks().none { it.name.equals(name, ignoreCase = true) })
                        "No smart action named $name."
                    else {
                        removeHook(name)
                        "Removed $name."
                    }
                }
                else -> {
                    showHooks = true
                    "Opening smart actions."
                }
            }
        }
        "miclang" -> {
            val wantHindi = hit.arg.lowercase().contains("hindi")
            if (wantHindi == hindiListen) {
                if (wantHindi) "Mic is already on Hindi." else "Mic is already on Auto."
            } else {
                toggleHindiListen()
                if (wantHindi) "Mic set to Hindi." else "Mic set to Auto."
            }
        }
        "voiceguard" -> {
            val a = hit.arg.lowercase()
            val off = a.contains("off") || a.contains("disable")
            val on = a.contains("on") || a.contains("enable")
            if (on == off) {
                "Voice guard is " + (if (voiceGuard) "on — locked commands need your name." else "off.") +
                    " Say “voice guard on” or “voice guard off”."
            } else {
                voiceGuard = on
                store.voiceGuard = on
                if (on) "Voice guard on — when locked, I'll only take commands with your name."
                else "Voice guard off."
            }
        }
        "voiceprint" -> {
            val a = hit.arg.lowercase()
            when {
                a.contains("enroll") || a.contains("register") || a.contains("set up") || a.contains("setup") || a.contains("add my") -> enrollVoiceprint()
                a.contains("remove") || a.contains("delete") || a.contains("clear") || a.contains("forget") -> removeVoiceprint()
                a.contains("strict") -> setVpSensitivity(1.4f, "strict")
                a.contains("loose") -> setVpSensitivity(2.4f, "loose")
                a.contains("normal") || a.contains("medium") -> setVpSensitivity(1.8f, "normal")
                else -> vpStatus()
            }
        }
        "reminders_ui" -> {
            showReminders = true
            "Opening reminders."
        }
        "backup" -> {
            exportBackup()
            "Opening the share sheet with your backup."
        }
        "battery" -> {
            val a = hit.arg.lowercase()
            if (a.contains("fix") || a.contains("unrestrict") || a.contains("optimiz") || a.contains("allow")) {
                requestBatteryUnrestricted()
                "Opening battery settings \u2014 set Jarvis to Unrestricted."
            } else {
                val pct = collectBriefing().batteryPct
                val pctTxt = if (pct >= 0) "$pct%" else "unknown"
                val state = if (batteryUnrestricted()) "unrestricted \u2713"
                else "optimized \u2014 say 'fix battery' so wake mode survives"
                "Battery at $pctTxt. Optimization: $state."
            }
        }
        "autostart" -> {
            if (openAutoStartSettings(getApplication())) "Opening autostart settings \u2014 allow Jarvis."
            else "This phone has no special autostart page \u2014 just keep battery unrestricted."
        }
        "whatsnew" -> {
            val e = CHANGELOG.firstOrNull()
            if (e == null) "No changelog yet."
            else "v${e.name}:\n" + e.features.joinToString("\n") { "\u2022 $it" }
        }
        "help" -> voiceHelp + "\n\u2022 \"My repos\" / \"Check builds for ...\""
        "shot" -> {
            takeScreenshot()
            "Taking a screenshot \u2014 approve the prompt, then the share sheet opens."
        }
        "access_setup" -> {
            openAccessSettings()
            "Opening Accessibility settings \u2014 turn on Jarvis screen control."
        }
        "access_tap" -> accessOrNeed { AccessBridge.tap(hit.arg) }
        "access_scroll" -> accessOrNeed { AccessBridge.scroll(hit.arg == "down") }
        "access_back" -> accessOrNeed { AccessBridge.back() }
        "access_recents" -> openRecentsScreen()
        "access_recents_tap" -> openAppFromRecents(hit.arg)
        "sched_msg" -> scheduleSchedMsg(parseScheduledMessage(hit.arg))
        "sched_list" -> listSchedMsgs()
        "sched_cancel" -> cancelSchedMsg(hit.arg)
        else -> "?"
    }

    private fun buildSystem(facts: List<String>): String {
        val master = if (masterInstalled) masterIdentity(masterName, masterAbout) + "\n" else ""
        val base = master + "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. " +
            "Be warm, a little witty, and helpful. Keep answers short enough for a phone screen unless asked for detail."
        if (facts.isEmpty()) return base
        return base + "\nThings you remember about your owner:\n" + facts.take(10).joinToString("\n") { "- $it" }
    }

    private fun persist() {
        val c = chats.firstOrNull { it.id == activeChatId } ?: return
        c.msgs.clear()
        c.msgs.addAll(messages.map { StoredMsg(if (it.role == "user") "user" else "model", it.text, it.time, it.imagePath ?: "") })
        if (c.title.isBlank() || c.title == "New chat") c.title = chatTitle(c.msgs)
        store.saveChats(chats)
        store.saveActiveId(activeChatId)
    }
}

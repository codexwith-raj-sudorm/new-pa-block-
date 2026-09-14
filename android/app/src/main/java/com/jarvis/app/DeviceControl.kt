package com.jarvis.app

/**
 * Device-control parsing. 100% JVM-pure (no android imports) so it is
 * unit-tested (see DeviceTest). The ViewModel executes the command.
 *
 * Understood (case-insensitive):
 * - "open YouTube" / "launch whatsapp app" / "start camera"
 * - "turn on the flashlight" / "torch off"
 * - "call mom" / "dial +919876543210" (places the call directly)
 * - "answer" / "hang up" / "speaker on" (in-call control)
 * - "text mom I'll be late" / "whatsapp ram hi" / "telegram launch at 6"
 * - "open mom's chat" / "open my whatsapp chat with ram"
 * - "turn on wifi" (opens the Wi-Fi panel — Android 10+ forbids silent toggles)
 * - "open settings"
 * - "silence my phone" / "turn off silent mode" (Do Not Disturb)
 */
sealed interface DeviceCommand
data class OpenApp(val name: String) : DeviceCommand
data class Torch(val on: Boolean) : DeviceCommand
data class CallContact(val query: String) : DeviceCommand
object Silence : DeviceCommand
object Unsilence : DeviceCommand
data class SetAlarm(val time: Pair<Int, Int>?) : DeviceCommand
data class SetTimer(val seconds: Int) : DeviceCommand
data class NavigateTo(val query: String) : DeviceCommand
data class WebSearch(val query: String) : DeviceCommand
data class PlayMedia(val query: String) : DeviceCommand
object WifiPanel : DeviceCommand
object SysSettings : DeviceCommand
enum class MsgApp { SMS, WHATSAPP, TELEGRAM }
data class TextMessage(val app: MsgApp, val contact: String, val body: String) : DeviceCommand
data class OpenChat(val app: MsgApp?, val contact: String) : DeviceCommand
object AnswerCall : DeviceCommand
object EndCall : DeviceCommand
data class Speaker(val on: Boolean) : DeviceCommand

fun parseDeviceCommand(raw: String): DeviceCommand? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val low = t.lowercase()

    // Torch ("turn on the flashlight", "torch off"). Bare "torch" falls through.
    if (listOf("flashlight", "flash light", "torch").any { low.contains(it) }) {
        val on = Regex("""\bon\b""").containsMatchIn(low)
        val off = Regex("""\boff\b""").containsMatchIn(low)
        if (on != off) return Torch(on)
    }

    // Speakerphone ("speaker on", "turn off the speaker", "speakerphone off").
    if (low.contains("speaker")) {
        val on = Regex("""\bon\b""").containsMatchIn(low)
        val off = Regex("""\boff\b""").containsMatchIn(low)
        if (on != off) return Speaker(on)
    }

    // Silence / unsilence (Do Not Disturb).
    val dndWord = low.contains("silent") || low.contains("silence") ||
        low.contains("do not disturb") || Regex("""\bdnd\b""").containsMatchIn(low)
    val offWord = Regex("""\b(off|disable|stop|exit)\b""").containsMatchIn(low)
    if (low.contains("unsilence") || low.contains("un-silence") ||
        Regex("""\b(sound|ringer) on\b""").containsMatchIn(low) || (dndWord && offWord)
    ) return Unsilence
    if (dndWord) return Silence

    // Wi-Fi panel (apps can't flip the switch since Android 10).
    if (low.contains("wifi") || low.contains("wi-fi") || low.contains("wi fi")) return WifiPanel

    // Call / dial.
    Regex("""^(call|dial|phone)\s+(.+)$""").find(t)?.let {
        val q = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
        if (q.isNotEmpty()) return CallContact(q)
    }

    // Text / WhatsApp / Telegram ("text mom I'll be late", "send a whatsapp to ram hi").
    // Bare "telegram <text>" (no "to <contact>") opens the share picker instead.
    Regex("""^(?:send(?: an?)?\s+)?(text(?: message)?|sms|message|whatsapp|telegram)(?: message)?\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(t)?.let {
            val appWord = it.groupValues[1].lowercase()
            val rest = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
            val app = when {
                appWord.startsWith("whatsapp") -> MsgApp.WHATSAPP
                appWord.startsWith("telegram") -> MsgApp.TELEGRAM
                else -> MsgApp.SMS
            }
            val toM = Regex("""^to\s+(.+)$""", RegexOption.IGNORE_CASE).find(rest)
            if (app == MsgApp.TELEGRAM && toM == null) {
                if (rest.isNotEmpty()) return TextMessage(MsgApp.TELEGRAM, "", rest)
            } else {
                val noTo = toM?.groupValues?.get(1)?.trim() ?: rest
                Regex("""^"([^"]+)"\s+(.+)$""").find(noTo)?.let { qm ->
                    val c = qm.groupValues[1].trim()
                    val b = qm.groupValues[2].trim()
                    if (c.isNotEmpty() && b.isNotEmpty()) return TextMessage(app, c, b)
                }
                val parts = noTo.split(Regex("""\s+"""), limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return TextMessage(app, parts[0].trim(), parts[1].trim())
                }
            }
        }

    // Open a chat ("open mom's chat", "open my whatsapp chat with ram").
    Regex("""^open\s+(?:my\s+)?(?:(whatsapp|telegram|sms|text)\s+)?chat\s+with\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(t)?.let {
            val c = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
            if (c.isNotEmpty()) return OpenChat(parseMsgApp(it.groupValues[1]), c)
        }
    Regex("""^open\s+(.+?)['\u2019]s\s+chat(?:\s+on\s+(whatsapp|telegram|sms|text))?$""", RegexOption.IGNORE_CASE)
        .find(t)?.let {
            val c = it.groupValues[1].trim()
            if (c.isNotEmpty()) return OpenChat(parseMsgApp(it.groupValues[2]), c)
        }

    // Answer / end calls ("answer", "pick up the phone", "hang up", "end the call").
    if (Regex("""^(answer|accept)( (the )?(call|phone|it))?$""", RegexOption.IGNORE_CASE).matches(t)) return AnswerCall
    if (Regex("""^(pick\s?up|pickup)( the)? (call|phone)$""", RegexOption.IGNORE_CASE).matches(t)) return AnswerCall
    if (Regex("""^(hang\s?up|hangup)$""", RegexOption.IGNORE_CASE).matches(t)) return EndCall
    if (Regex("""^(end|stop|reject|decline)( the)? (call|phone)$""", RegexOption.IGNORE_CASE).matches(t)) return EndCall

    // Alarm ("wake me at 7", "set an alarm for 6:30 am"). No time -> clock app.
    if (low.contains("alarm") || low.startsWith("wake me")) {
        return SetAlarm(parseAlarmTime(t))
    }

    // Timer ("set a timer for 5 minutes", "countdown 10 sec").
    if (low.contains("timer") || low.contains("countdown") || low.contains("stopwatch")) {
        return SetTimer(parseDuration(t) ?: 60)
    }

    // Navigate ("navigate to Andheri station", "directions to work").
    Regex("""^(navigate to|directions to|take me to|drive to)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(t)?.let {
            val q = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
            if (q.isNotEmpty()) return NavigateTo(q)
        }

    // Web search ("search for monsoon recipes", "google Taj Mahal").
    Regex("""^(search(?: the web)?(?: for)?|google|look up)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(t)?.let {
            val q = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
            if (q.isNotEmpty()) return WebSearch(q)
        }

    // Play ("play Believer", "play some jazz").
    Regex("""^play\s+(.+)$""", RegexOption.IGNORE_CASE).find(t)?.let {
        val q = it.groupValues[1].trim().trimEnd('?', '.', '!').trim()
        if (q.isNotEmpty()) return PlayMedia(q)
    }

    // Open / launch (with settings shortcuts).
    Regex("""^(open|launch|start)\s+(.+)$""", RegexOption.IGNORE_CASE).find(t)?.let {
        var name = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
        val nl = name.lowercase()
        if (nl == "settings" || nl == "phone settings") return SysSettings
        if (nl.contains("wifi")) return WifiPanel
        name = Regex("""\s+app$""", RegexOption.IGNORE_CASE).replace(name, "").trim()
        if (name.isNotEmpty()) return OpenApp(name)
    }
    return null
}

/** Parse "7", "7am", "7:30", "7:30 pm" into 24h (hour, min). Pure, tested. */
fun parseAlarmTime(raw: String): Pair<Int, Int>? {
    val m = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?""", RegexOption.IGNORE_CASE)
        .find(raw) ?: return null
    var h = m.groupValues[1].toIntOrNull() ?: return null
    val min = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
    if (min > 59) return null
    val ap = m.groupValues[3].lowercase().replace(".", "")
    if (ap == "pm" && h < 12) h += 12
    if (ap == "am" && h == 12) h = 0
    if (h !in 0..23) return null
    return h to min
}

/** Parse "5 minutes", "10 sec", "1 hour 30 minutes" into seconds. Pure, tested. */
fun parseDuration(raw: String): Int? {
    var total = 0
    var found = false
    val rx = Regex("""(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""")
    for (m in rx.findAll(raw.lowercase())) {
        val n = m.groupValues[1].toIntOrNull() ?: continue
        val u = m.groupValues[2]
        total += when {
            u.startsWith("h") -> n * 3600
            u.startsWith("m") -> n * 60
            else -> n
        }
        found = true
    }
    return if (found && total in 1..86400) total else null
}

/** Map "whatsapp" / "telegram" / "sms" / "text" to [MsgApp]. Pure, tested. */
fun parseMsgApp(word: String): MsgApp? = when (word.lowercase()) {
    "whatsapp" -> MsgApp.WHATSAPP
    "telegram" -> MsgApp.TELEGRAM
    "sms", "text" -> MsgApp.SMS
    else -> null
}

/** Normalize a dial string for wa.me: digits only + best-effort country code. Pure, tested. */
fun waDigits(number: String, defaultCountry: String = ""): String {
    var d = number.filter { it.isDigit() }
    if (d.length == 10 && defaultCountry == "IN") d = "91$d"
    return d
}

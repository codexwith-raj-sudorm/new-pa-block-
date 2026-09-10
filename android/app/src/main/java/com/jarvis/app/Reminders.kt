package com.jarvis.app

/**
 * Reminder parsing + formatting. 100% JVM-pure (no android imports) so it is
 * unit-tested (see ReminderTest). The ViewModel turns a [ReminderRequest] into an
 * AlarmManager alarm; [ReminderItem] is what the Store persists.
 *
 * Understood (case-insensitive):
 * - "remind me in 10 minutes to drink water" / "in 2 hours to call mom"
 * - "remind me at 5pm to gym" / "at 9:30 am standup" / "tomorrow at 9am ..."
 */
data class ReminderItem(val id: Int, val at: Long, val text: String)

sealed interface ReminderWhen
data class InMinutes(val minutes: Int) : ReminderWhen
data class AtTime(val hour: Int, val minute: Int, val tomorrow: Boolean) : ReminderWhen

data class ReminderRequest(val whenAt: ReminderWhen, val text: String)

private val IN_RX = Regex("""\bin\s+(\d+)\s*(minutes?|mins?|hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
private val AT_RX = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)
private val LEAD_FILLER_RX = Regex("""^(to|about|that)\s+""", RegexOption.IGNORE_CASE)

fun parseReminder(raw: String): ReminderRequest? {
    val t0 = raw.trim()
    if (!t0.startsWith("remind me", ignoreCase = true)) return null
    val t = t0.substring(9).trim()
    if (t.isEmpty()) return null
    val tomorrow = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)

    IN_RX.find(t)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2].lowercase()
        val minutes = if (unit.startsWith("h")) n * 60 else n
        if (minutes < 1) return null
        return ReminderRequest(InMinutes(minutes), cleanReminderText(t, m.range))
    }
    AT_RX.find(t)?.let { m ->
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = if (m.groupValues[2].isEmpty()) 0 else (m.groupValues[2].toIntOrNull() ?: return null)
        if (h > 23 || min > 59) return null
        when (m.groupValues[3].lowercase()) {
            "pm" -> if (h < 12) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h > 23) return null
        return ReminderRequest(AtTime(h, min, tomorrow), cleanReminderText(t, m.range))
    }
    return null
}

private fun cleanReminderText(t: String, span: IntRange): String {
    var s = (t.substring(0, span.first) + " " + t.substring(span.last + 1)).trim()
    s = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).replace(s, "").trim()
    s = LEAD_FILLER_RX.replace(s, "").trim()
    s = s.trimEnd('?', '.', '!').trim()
    return s.ifBlank { "Time's up!" }
}

/** Human "when" for confirmations and the list ("in 5 min", "at 5:30 PM" ...). */
fun dueText(at: Long, now: Long): String {
    val mins = ((at - now) / 60_000).toInt()
    if (mins < 1) return "any moment now"
    if (mins < 60) return "in $mins min"
    val h = mins / 60
    val m = mins % 60
    if (h < 24) return if (m == 0) "in $h h" else "in $h h $m min"
    val fmt = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault())
    return "on " + fmt.format(java.util.Date(at))
}

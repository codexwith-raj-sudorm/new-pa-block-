package com.jarvis.app

/**
 * Scheduled messaging: "text mom I'll be late tomorrow at 9am".
 * 100% JVM-pure (no android imports) so it is unit-tested (see SchedMsgTest).
 * The ViewModel resolves the contact, persists the item and arms an alarm;
 * [SchedMsgReceiver] fires the actual send.
 */
data class SchedMsgItem(
    val id: Int, val at: Long, val app: String,
    val label: String, val number: String, val body: String
)

data class SchedMsgRequest(
    val app: MsgApp, val contact: String, val body: String, val whenAt: ReminderWhen
)

private val SCHED_RX = Regex(
    """\b(in\s+\d+\s*(?:minutes?|mins?|hours?|hrs?|h)|tomorrow\s+at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s*\??$""",
    RegexOption.IGNORE_CASE
)
private val VERB_RX = Regex(
    """^(send|text|sms|message|whatsapp|telegram)\s+(.+)$""",
    RegexOption.IGNORE_CASE
)

/**
 * Parse "send <body> to <contact> <when>" or "<verb> <contact> <body> <when>".
 * Returns null when there is no schedule clause (caller falls back to send-now).
 * Pure, tested.
 */
fun parseScheduledMessage(raw: String): SchedMsgRequest? {
    val t = raw.trim()
    val verb = VERB_RX.find(t) ?: return null
    val app = when (verb.groupValues[1].lowercase()) {
        "whatsapp" -> MsgApp.WHATSAPP
        "telegram" -> MsgApp.TELEGRAM
        else -> MsgApp.SMS
    }
    val rest = verb.groupValues[2].trim()
    val sm = SCHED_RX.find(rest) ?: return null
    val req = parseReminder(reminderInput(sm.groupValues[1])) ?: return null
    val part = rest.substring(0, sm.range.first).trim().trimEnd('?', '.', '!', ',').trim()
    if (part.isEmpty()) return null
    val toIdx = part.lastIndexOf(" to ", ignoreCase = true)
    val (contact, body) = if (toIdx >= 0) {
        part.substring(toIdx + 4).trim().trimEnd(',', '.', '?', '!').trim() to
            part.substring(0, toIdx).trim()
    } else {
        val bits = part.split(Regex("""\s+"""), limit = 2)
        if (bits.size < 2) return null
        bits[0].trimEnd(',', '.', '?', '!').trim() to bits[1].trim()
    }
    if (contact.isEmpty() || body.isEmpty()) return null
    return SchedMsgRequest(app, contact, body, req.whenAt)
}

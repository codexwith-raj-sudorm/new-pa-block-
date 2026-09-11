package com.jarvis.app

/**
 * Device-control parsing. 100% JVM-pure (no android imports) so it is
 * unit-tested (see DeviceTest). The ViewModel executes the command.
 *
 * Understood (case-insensitive):
 * - "open YouTube" / "launch whatsapp app" / "start camera"
 * - "turn on the flashlight" / "torch off"
 * - "call mom" / "dial +919876543210" (opens the dialer — never auto-calls)
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
object WifiPanel : DeviceCommand
object SysSettings : DeviceCommand

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

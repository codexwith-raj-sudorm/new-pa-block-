package com.jarvis.app

/**
 * Update history, major features only. Newest first.
 * Powers the first-open "What's new" popup and the history in Settings.
 * 100% JVM-pure (see ChangelogTest). Add a new entry on top with every release.
 */
data class ChangelogEntry(val code: Int, val name: String, val features: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        15, "2.4", listOf(
            "Bubble ring becomes a live audio visualizer while listening/speaking",
            "Color states: green listening, amber thinking, gray offline",
            "Snap-to-edge docking, HUD status ticker, Stark chimes"
        )
    ),
    ChangelogEntry(
        14, "2.3", listOf(
            "What's-new popup on first open of every update",
            "Full update history in Settings"
        )
    ),
    ChangelogEntry(
        13, "2.2", listOf(
            "Mini arc-reactor home-screen widget",
            "Tap = wake on/off; tap while Jarvis speaks = interrupt"
        )
    ),
    ChangelogEntry(
        12, "2.1", listOf(
            "Stark HUD arc-reactor bubble with rotating telemetry ring",
            "Bubble glows with state and grows with your voice"
        )
    ),
    ChangelogEntry(
        11, "2.0", listOf(
            "Todos & notes Lists screen (📝 button up top)",
            "Voice: “add milk to my list”, “done 2”, “note …”"
        )
    ),
    ChangelogEntry(
        10, "1.9", listOf(
            "Device control: “open YouTube”, torch, dial contacts, settings",
            "Camera + contacts permission asked on demand"
        )
    ),
    ChangelogEntry(
        9, "1.8", listOf(
            "Wake word + reminders auto re-arm after reboot",
            "Battery-optimization prompt so wake isn't killed"
        )
    ),
    ChangelogEntry(
        8, "1.7", listOf(
            "Reminders: “remind me in 10 min / at 5pm …” with notifications",
            "“my reminders” lists them, “cancel reminder N” drops one"
        )
    ),
    ChangelogEntry(
        7, "1.6", listOf(
            "7 named voices: Jarvis + 3 male + 3 female",
            "Each previews in its own voice; wake “Yes?” matches"
        )
    ),
    ChangelogEntry(
        6, "1.5", listOf(
            "Bubble pulses with real mic level, still in silence",
            "Google start-beep eliminated on every stream"
        )
    ),
    ChangelogEntry(
        5, "1.4", listOf(
            "Floating bubble over any app + background wake service",
            "Persistent listening notification with Stop"
        )
    ),
    ChangelogEntry(
        4, "1.3", listOf(
            "“Hey Jarvis” wake word",
            "Models list hidden unless you use your own key"
        )
    ),
    ChangelogEntry(
        3, "1.2", listOf(
            "Spoken replies in a male voice",
            "Clean speech — no emoji read aloud"
        )
    ),
    ChangelogEntry(
        1, "1.0", listOf(
            "Chat with Gemini + memory",
            "Offline time, calculator, memory"
        )
    )
)

/** Entries newer than [sinceCode], newest first. */
fun whatsNew(sinceCode: Int): List<ChangelogEntry> =
    CHANGELOG.filter { it.code > sinceCode }

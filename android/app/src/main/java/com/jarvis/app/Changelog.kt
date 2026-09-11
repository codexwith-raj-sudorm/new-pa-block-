package com.jarvis.app

/**
 * Update history, major features only. Newest first.
 * Powers the first-open "What's new" popup and the history in Settings.
 * 100% JVM-pure (see ChangelogTest). Add a new entry on top with every release.
 */
data class ChangelogEntry(val code: Int, val name: String, val features: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        41, "5.0", listOf(
            "Share button on code blocks: send snippets anywhere",
            "Delete needs a second tap — no more accidental chat loss"
        )
    ),
    ChangelogEntry(
        40, "4.9", listOf(
            "Widget shows your master name: Sat, 12 Sep · Master Raj",
            "Clear all chats with tap-again confirm"
        )
    ),
    ChangelogEntry(
        39, "4.8", listOf(
            "Rename any chat: tap the pencil in the chats list"
        )
    ),
    ChangelogEntry(
        38, "4.7", listOf(
            "Welcome greeting now says your name: Welcome back, Master Raj"
        )
    ),
    ChangelogEntry(
        37, "4.6", listOf(
            "One-tap backup: export all chats, memories, todos, hooks, reminders",
            "Briefing widget: tap battery to refresh instantly"
        )
    ),
    ChangelogEntry(
        36, "4.5", listOf(
            "Master Raj Thakur baked into the code — recognized on every device",
            "First wake each morning/afternoon/evening greets “Master Raj”"
        )
    ),
    ChangelogEntry(
        35, "4.4", listOf(
            "Master Card: share your identity to other devices, import by paste",
            "Baked-in master: APKs built with MASTER_IDENTITY recognize you instantly"
        )
    ),
    ChangelogEntry(
        34, "4.3", listOf(
            "Master Key: install it and Jarvis recognizes you as his Master",
            "Core identity memory: creator recognition + your personal notes"
        )
    ),
    ChangelogEntry(
        33, "4.2", listOf(
            "Briefing widget: time, battery, next reminder on your home screen",
            "Double-tap the HUD bubble to hush Jarvis mid-speech"
        )
    ),
    ChangelogEntry(
        32, "4.1", listOf(
            "First-run setup wizard: mic, wake word, battery in 3 taps",
            "Reminders manager: see and cancel alarms in one place"
        )
    ),
    ChangelogEntry(
        31, "4.0", listOf(
            "Live currency converter: “100 dollars in rupees”",
            "Calculator understands “percent” + README refresh"
        )
    ),
    ChangelogEntry(
        30, "3.9", listOf(
            "Morning briefing: daily 8 AM notification with battery + weather"
        )
    ),
    ChangelogEntry(
        29, "3.8", listOf(
            "Voice Studio: speech rate + pitch sliders in Settings"
        )
    ),
    ChangelogEntry(
        28, "3.7", listOf(
            "Proper arc-reactor launcher icon (no more default robot)",
            "Hindi mic toggle: voice input in Hindi"
        )
    ),
    ChangelogEntry(
        27, "3.6", listOf(
            "“Read my notifications” — Jarvis summarizes your latest alerts"
        )
    ),
    ChangelogEntry(
        26, "3.5", listOf(
            "Quick Settings tile: toggle wake word from the notification shade",
            "Launcher shortcuts: long-press the icon for New chat / Briefing"
        )
    ),
    ChangelogEntry(
        25, "3.4", listOf(
            "Smart actions: teach Jarvis webhooks, then say “turn on …”"
        )
    ),
    ChangelogEntry(
        24, "3.3", listOf(
            "Message timestamps — every bubble shows its time, saved forever"
        )
    ),
    ChangelogEntry(
        23, "3.2", listOf(
            "Real weather: “Mumbai weather” or “will it rain” — no API key needed"
        )
    ),
    ChangelogEntry(
        22, "3.1", listOf(
            "Everyday tools: alarms, timers, navigation, web search, play music",
            "Unit converter, dice, coin flip, jokes, good-morning routine"
        )
    ),
    ChangelogEntry(
        21, "3.0", listOf(
            "Retry button on failed replies — one tap to regenerate",
            "Share any chat as text to WhatsApp, Gmail, anywhere"
        )
    ),
    ChangelogEntry(
        20, "2.9", listOf(
            "Briefing card: battery, memory, storage, network at a glance",
            "Search your chats + starter chips on empty chats"
        )
    ),
    ChangelogEntry(
        19, "2.8", listOf(
            "Memory Vault: facts move to a local Room database",
            "Self-healing privacy: memories older than 30 days auto-expire",
            "Seamless migration — your existing memories carry over"
        )
    ),
    ChangelogEntry(
        18, "2.7", listOf(
            "Share Hub: share any text to Jarvis — summarize, ELI5, bug-hunt, translate",
            "“Silence my phone” / “unsilence” device command",
            "Hands-free mode: mic re-opens after every reply"
        )
    ),
    ChangelogEntry(
        17, "2.6", listOf(
            "Stark cinematic theme: obsidian + gold + cyan",
            "Code Terminal: fenced code renders with Copy button",
            "HUD listening state goes white-hot per spec"
        )
    ),
    ChangelogEntry(
        16, "2.5", listOf(
            "Voice gender mismatches fixed (male names get male voices)",
            "Hamburger menu: home screen keeps Wake, Chats, Settings only",
            "Settings Updates section (collapsible history)"
        )
    ),
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

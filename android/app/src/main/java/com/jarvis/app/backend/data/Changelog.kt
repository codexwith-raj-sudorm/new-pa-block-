package com.jarvis.app.backend.data

/**
 * Update history, major features only. Newest first.
 * Powers the first-open "What's new" popup and the history in Settings.
 * 100% JVM-pure (see ChangelogTest). Add a new entry on top with every release.
 */
data class ChangelogEntry(val code: Int, val name: String, val features: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        72, "6.21", listOf(
            "Zero-API media router: 'play X' opens your music or YouTube app directly",
            "First-time app chooser with remember; System default anytime",
            "Spoken handoff plus web fallback when no app can play it",
            "Overlay dismiss now cancels active AI/network work",
            "Public release builds are BYOK with R8 shrink/obfuscation enabled",
            "Screen vision shows a privacy notice and refuses protected/sensitive frames",
            "Other-AI endpoint validation blocks local/private targets in release builds"
        )
    ),
    ChangelogEntry(
        71, "6.20", listOf(
            "Neural-link onboarding: glass auth module with 3-phase establish sequence",
            "Identity confirmation with avatar, verified badge and clearance pill"
        )
    ),
    ChangelogEntry(
        70, "6.19", listOf(
            "Proactive reflexes: Jarvis speaks up for low battery + incoming messages",
            "Gemini persona voice with offline fallback lines, ducked under music",
            "DND, cooldown, call and pocket guardrails; toggle in Settings"
        )
    ),
    ChangelogEntry(
        69, "6.18", listOf(
            "Dynamic Island: black pill drops from the camera cutout when standby arms",
            "Collapses to a green dot; the wake word strikes it back open with haptics",
            "[SYS] LISTENING / OVERRIDE readout with a live neon waveform"
        )
    ),
    ChangelogEntry(
        68, "6.17", listOf(
            "Time-boxed standby: the listener lives only inside your window",
            "Wake pop: Jarvis seizes the screen over the lock screen",
            "Stealth notification + offline-first wake recognition"
        )
    ),
    ChangelogEntry(
        67, "6.16", listOf(
            "Gesture navigation: edge-swipe drawer with chats, memory, lists + voice/wake toggles",
            "Floating header (menu, status pill, avatar) + top-right profile hub",
            "Tap the status pill to stop Jarvis mid-speech"
        )
    ),
    ChangelogEntry(
        66, "6.15", listOf(
            "Compact island pill, chats flow up both sides",
            "Unified glass menu, identity handshake and chats from your mockup"
        )
    ),
    ChangelogEntry(
        65, "6.14", listOf(
            "Stable app signing: new builds update in place, chats/keys/permissions stay"
        )
    ),
    ChangelogEntry(
        64, "6.13", listOf(
            "Jarvis addresses you as sir",
            "Private/dev master unlocks the optional built-in brain and repo access"
        )
    ),
    ChangelogEntry(
        63, "6.12", listOf(
            "Master install connects the brain instantly + shows key status",
            "Settings tells you when the build has no built-in key"
        )
    ),
    ChangelogEntry(
        62, "6.11", listOf(
            "Floating dynamic island title, chats dim around it and the dialogue",
            "Idle bubble always docks after 45s (stuck voice flags can't block it)",
            "Master identity restores itself after reinstall (no re-typing)",
            "Private/dev master key revocable without typing it",
            "CI toolchain: Gradle 8.14.5, pinned Ubuntu 24.04"
        )
    ),
    ChangelogEntry(
        61, "6.10", listOf(
            "Hold-summon shows your screen untouched (island floats, zero dimming)",
            "Hub bubble auto-docks to the edge when idle (wake keeps listening)",
            "Tap pops it out, double-tap turns wake off, hold opens wake screen",
            "Drag it to the border to hide it, like the accessibility icon"
        )
    ),
    ChangelogEntry(
        60, "6.9", listOf(
            "System Calibration tour: spotlight + typewriter guide",
            "1.5s entry beat, core-mic-execute-handoff steps",
            "Always skippable, crash-safe, master screens excluded"
        )
    ),
    ChangelogEntry(
        59, "6.8", listOf(
            "First-run profile: Jarvis asks your name + details",
            "Say 'my name is X' anytime to update it",
            "AI personalizes replies with who you are",
            "Header + input ride on scrims — messages never merge under them",
            "Swirl core stays on behind the chat after first message"
        )
    ),
    ChangelogEntry(
        58, "6.7", listOf(
            "Hardware requests get an honest refusal (no LLM hallucination)",
            "Owner-card master keys stay owner-grade after updates"
        )
    ),
    ChangelogEntry(
        57, "6.6", listOf(
            "Chat area strictly bounded — bottom bar always visible",
            "Flashlight control removed (no hardware toggles)",
            "Voice warnings auto-dismiss after 4 seconds",
            "Private/dev keys activate on owner-grade master only",
            "API keys + master key move to encrypted storage"
        )
    ),
    ChangelogEntry(
        56, "6.5", listOf(
            "Share entry is a floating sheet — no more black fullscreen",
            "Fresh chats start empty (no pre-seeded bot messages)",
            "Wake word answers instantly — mic opens before the greeting",
            "Fuzzy wake matcher catches misheard variants (jervis, davis)"
        )
    ),
    ChangelogEntry(
        55, "6.4", listOf(
            "New app icon: orbital quantum core on dark glass",
            "Home widget restyled to match the new icon"
        )
    ),
    ChangelogEntry(
        54, "6.3", listOf(
            "Onboarding permission ticks refresh live after granting",
            "Home hero stays until you speak (survives master install)",
            "“Brain connected” only announced when the brain actually wakes"
        )
    ),
    ChangelogEntry(
        53, "6.2", listOf(
            "Wake screen: harmonic core with ripples, data rings, live transcription",
            "Floating hub bubble: diamond hub with idle/wake/listening states",
            "System Config sheet: security card, neural engine, gesture toggle"
        )
    ),
    ChangelogEntry(
        52, "6.1", listOf(
            "Premium home: neon-green glass UI with a living swirl core",
            "Hero greeting, context card, and one-tap “Execute Script”",
            "Floating chat pill with mic ring; quick tiles for Briefing, Hooks, Chats",
            "Assist island: neon edge flash, AI core, ask-about-screen sheet"
        )
    ),
    ChangelogEntry(
        51, "6.0", listOf(
            "Default assistant: set Jarvis to answer the hold-gesture (Settings)",
            "Assist overlay: hologram HUD with one-tap “ask about screen”",
            "Share any image to Jarvis: describe it or read its text",
            "Share text to Jarvis: one-tap summarize, then inject to chat"
        )
    ),
    ChangelogEntry(
        50, "5.9", listOf(
            "Screen control: “take a screenshot”, “what's on my screen”, “tap …”",
            "Scheduled messages: “text mom I'll be late tomorrow at 9am”",
            "WhatsApp auto-send via accessibility tap, SMS fully automatic",
            "“my scheduled messages” lists them, “cancel scheduled message N” drops one",
            "Voice reliability: mic handoff fixed, hands-free commands over any app",
            "Master voice guard: locked commands need your name",
            "Voiceprint: enroll your voice — strangers are ignored",
            "Image generation: “generate an image of …”"
        )
    ),
    ChangelogEntry(
        49, "5.8", listOf(
            "HUD design system: all dialogs and fields themed",
            "Chat message actions: speak, copy, retry",
            "Wake orbit v2: tappable focus panels, NET orbiter",
            "Motion pass: shimmer thinking, haptics, animations"
        )
    ),
    ChangelogEntry(
        48, "5.7", listOf(
            "GitHub by voice: repos, status, builds, issues, read files",
            "Private/dev keys auto-activate with the Master Key — zero typing",
            "Master Key stamps Raj identity and re-hides after install",
            "Typed questions stay silent; settings features are voice tools"
        )
    ),
    ChangelogEntry(
        47, "5.6", listOf(
            "Wake-mode interface: neural brain with orbiting live panels",
            "Typed questions stay silent — only voice answers speak",
            "All settings features now voice commands; settings stripped lean",
            "Origin marks and copyright protection embedded"
        )
    ),
    ChangelogEntry(
        46, "5.5", listOf(
            "24x7 standby: heartbeat watchdog, auto-revive, call-aware wake",
            "Answer, hang up and speaker calls by voice",
            "OEM autostart shortcuts for Xiaomi, Oppo, Vivo and more",
            "Standby never dies on errors — pauses and resumes itself"
        )
    ),
    ChangelogEntry(
        45, "5.4", listOf(
            "Conversation mode: say Hey Jarvis once, talk till 10s of silence",
            "Direct calling plus SMS, WhatsApp and Telegram texting",
            "Open contact chats straight from voice",
            "Bubble tap interrupts speech; nearby-voice noise gate",
            "Battery-unrestricted flow actually works now"
        )
    ),
    ChangelogEntry(
        44, "5.3", listOf(
            "Reactive arc-core HUD: core breathes with voice, radar sweep, live readouts",
            "HUD chrome: grid backdrop, slim status bar, interrupt mini-reactor",
            "Option diet: dead settings removed, share + briefing moved into context",
            "Bugfix rollup: reply routing, TTS chunks, widgets, muter, chat titles"
        )
    ),
    ChangelogEntry(
        43, "5.2", listOf(
            "Tap the arc reactor to interrupt speech, even hands-free",
            "Reminder quick-add inside the dialog",
            "Dashboard tap-to-refresh with live battery",
            "3D neural reactor core + fading header mini-reactor",
            "Priya voice unified in the wake service"
        )
    ),
    ChangelogEntry(
        42, "5.1", listOf(
            "Stark HUD retheme: golden header, neural cards, brain core, live dashboard",
            "Stark Hub share intercept with auto-inject to Jarvis",
            "Stark wake widget + biometric Stark ID lock",
            "Privacy vault with 90-day auto-purge",
            "Fixed Priya voice, hidden master section, slim 4-item menu"
        )
    ),
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
            "Owner Raj Thakur identity metadata in app; secrets stay outside source",
            "First wake each morning/afternoon/evening greets “Master Raj”"
        )
    ),
    ChangelogEntry(
        35, "4.4", listOf(
            "Master Card: share your identity to other devices, import by paste",
            "Private/dev master: builds with MASTER_IDENTITY recognize you instantly"
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

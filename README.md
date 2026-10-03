# Jarvis 🤖 — Android app

Personal AI assistant as a **native Android app** (Kotlin + Compose).
No server, no hosting, no sleep — the app talks to Google Gemini straight from your phone.

![android](https://github.com/codexwith-raj-sudorm/new-pa-block-/actions/workflows/android.yml/badge.svg?branch=arena/01a08a6b-new-pa-block)

## Features (v6.21)

- 🧭 Gesture navigation: edge-swipe drawer (chats, memory, lists, voice/wake toggles) + floating header + profile hub — tap the status pill to stop speech
- 🕰️ Time-boxed standby: the listener lives only inside your window (default 08:00–18:00); wake word pops Jarvis over the lock screen
- 🏝️ Dynamic Island: black pill drops from the camera cutout when standby arms, collapses to a green dot, bursts open on wake with haptics
- 📡 Proactive reflexes: unprompted voice for low battery + incoming messages (Gemini persona, offline fallback, DND/cooldown/pocket guardrails)
- 🔐 Neural-link onboarding: glass auth module with a 3-phase establish sequence + clearance pill
- 🎵 Zero-API media router: “play X” opens Spotify/YouTube directly — first-time app chooser with remember
- 💬 Chat with Gemini + auto model discovery, multi-chat with auto-titles, rename/search/delete, export + full backup
- 🎙️ Voice I/O: in-app mic (beeps muted on every stream) + “Hey Jarvis” wake word with fuzzy matching + fixed Priya voice
- 🧠 Memory vault (Room, auto-expiry) + todos/notes/lists, reminders + alarms, scheduled messages
- 🛠️ Everyday tools: screen control, calls/SMS, alarms, timers, navigation, web search, weather, calculator, converters, jokes
- ⚙️ Settings: optional own key, preferred model, custom OpenAI-compatible endpoints, standby window, reflexes, playback app
- 🎨 Premium green-glass HUD theme + orbital icon + widgets + Quick Settings tile + shortcuts
- 🔒 Hardware-safe by design (no torch/brightness/Wi-Fi toggles) · 🔐 keys in encrypted storage · sir, always

Full list: [FEATURES.md](FEATURES.md) · in-app history: Settings → updates · specs: [docs/](docs/README.md)

## Install (phone, no PC needed)

1. Open this repo on GitHub → **Actions** tab → latest green `android` run
2. Download **jarvis-apk** under Artifacts (it's a `.zip` — unzip it)
3. Open the `.apk` → **Install** (allow “install unknown apps” if asked)
4. Open **Jarvis** and chat! 🎉 Private builds can start online immediately when local/CI secrets are configured.

Every push to this branch rebuilds the APK automatically. Updates install in place — chats, keys and permissions stay.

## Private build keys

This branch is optimized for a personal APK. Put secrets in untracked `local.properties` (or CI environment secrets) and Gradle injects them into `BuildConfig` for both debug and release builds:

```properties
GEMINI_API_KEY="AIza..."
GH_READ_TOKEN="ghp_..."
MASTER_IDENTITY='{"k":"optional-key","n":"Raj","a":"West Bengal, India"}'
MASTER_KEY="optional-key"
```

User-entered keys in Settings still override the built-in private defaults. Keep APKs with injected keys on owned devices only, restrict token/API scopes, and rotate if a private APK leaks.

## Private identity

Entering **Raj** in onboarding resolves the local role as **Admin** across the hero card and profile hub. The old Master Key panel is intentionally hidden from the user-facing Settings UI to keep the personal build clean.

## Project layout

```
android/                  # native app (the product)
  app/src/main/java/com/jarvis/app/
    frontend/screens/     # Compose UI: chat, HUD, assist, share sheet, wake
    frontend/design/      # theme, config panel, nav drawer, auth gate, media
    frontend/widgets/     # home-screen widgets
    backend/brain/        # ViewModel + Gemini REST + router + media router
    backend/system/       # wake service, standby, island, reflexes, receivers
    backend/device/       # device-command parsing (hardware-safe)
    backend/voice/        # voice loop, voiceprint, TTS helpers
    backend/data/         # vault DB, lists, reminders, changelog
    backend/ai/           # providers, personas, image generation
    backend/net/          # GitHub API + encrypted token storage
  app/src/test/...        # 262 unit tests, JVM-pure
docs/                     # product specs + inline HTML mockups (see docs/README.md)
.github/workflows/        # android.yml (tests + APK) · keycheck.yml (private/dev key health)
FEATURES.md               # full feature list
renovate.json             # grouped Gradle + Actions updates
```

## Dev notes

- Min SDK 26 (Android 8) · builds on JDK 17 + AGP 8.5.2 (CI does it)
- Pure logic lives in testable top-level functions; every release adds tests + updates the changelog list too.

# Jarvis 🤖 — Android app

Personal AI assistant as a **native Android app** (Kotlin + Compose).
No server, no hosting, no sleep — the app talks to Google Gemini straight from your phone.

![android](https://github.com/codexwith-raj-sudorm/new-pa-block-/actions/workflows/android.yml/badge.svg?branch=arena/01a08a6b-new-pa-block)

## Features (v6.7)

- 💬 Chat with Gemini + auto model discovery, 20-message context, on-device history
- 💬 Multi-chat: new / switch / delete chats with auto-titles (fresh chats start empty)
- 🧠 Memory manager: view/add/delete memories (or “remember …” in chat)
- 🎙️ Voice I/O: in-app mic (no Google popup/beeps — all streams muted around every listen) + "Hey Jarvis" wake word with instant mic, fuzzy matching (catches "jervis"/"davis"), and an arc-reactor HUD bubble over any app + fixed Priya voice with mute toggle
- ⏰ Offline tools (no key needed): time, calculator, memory, reminders, silence, calls, app launch
- 📝 Todos & notes: checkable lists ("add milk to my list", "done 2") + quick notes — all offline
- 🔋 Wake word + reminders survive reboot (auto re-arm) + battery-optimization prompt
- 🧲 Mini arc-reactor home-screen widget: tap to arm wake mode, tap while Jarvis speaks to interrupt
- 🆕 What's-new popup on every update + full update history in Settings
- 🌀 HUD states: live waveform ring, color-coded modes, status ticker, interface chimes
- ⚙️ Settings: optional own key, preferred model, custom OpenAI-compatible endpoints, refresh models
- 🎨 Premium green glass theme + orbital app icon + voice warnings that auto-dismiss
- 💻 Code Terminal: fenced code renders in monospace blocks with Copy (TTS skips code)
- ⚡ Share Hub: floating sheet over any app — summarize text, describe/read shared images, inject to chat
- 🔇 Device silence: “silence my phone” / “unsilence” via Do Not Disturb
- 🔁 Hands-free mode: mic re-opens after every reply (menu toggle)
- 🗄️ Memory Vault: facts in Room DB with 30-day auto-expiry + seamless migration
- 📊 Briefing card: battery, memory, storage, network + chat search + starter chips
- ↻ One-tap retry on failed replies + 📤 export any chat as text
- 🛠️ Everyday tools: alarms, timers, navigation, web search, play from YouTube
- 🔄 Unit + live currency converter, dice, coin, jokes, good-morning routine
- 🌦️ Keyless weather, 🔌 smart-home webhooks, 🕒 timestamped bubbles
- 🔔 Notification reader, ☀ 8 AM briefing, QS tile, shortcuts, Hindi mic, voiceprint guard
- 🔒 Hardware-safe by design: no torch/brightness/Wi-Fi toggles (refused with an honest reply)
- 🔐 API keys + master key in encrypted storage; baked owner keys unlock on owner-grade master only

## Install (phone, no PC needed)

1. Open this repo on GitHub → **Actions** tab → latest green `android` run
2. Download **jarvis-apk** under Artifacts (it's a `.zip` — unzip it)
3. Open the `.apk` → **Install** (allow "install unknown apps" if asked)
4. Open **Jarvis** and chat! 🎉 (Paste your own free key from `aistudio.google.com` in ⚙️ Settings — or install the owner Master Key to use the baked-in key.)

Every push to this branch rebuilds the APK automatically.

## Built-in default key (owner setup)

The APK bakes in a locked default key from the `GEMINI_API_KEY` repo secret (never in git).
It activates only while an owner-grade Master Key is installed; other users paste their own key in ⚙️ Settings.
In ⚙️ Settings the built-in key can't be viewed, changed, removed or overridden.

1. Create a key at `aistudio.google.com` → **restrict it to the Generative Language API**
2. Repo → **Settings → Secrets and variables → Actions** → New repository secret `GEMINI_API_KEY`
3. Re-run the latest `android` workflow (⋯ → Re-run jobs) → the new APK has the key baked in

⚠️ Obfuscation ≠ encryption: anyone decompiling the APK can recover the key.
Mitigations: API-restrict the key, keep the APK private, rotate the key if it leaks.

## Master identity on every device (no retyping)

Two ways — pick either:
- **Master Card (easiest):** on your main phone go to ⚙️ → Master Key → **Share master card** → send it to your other device → on the other device paste it into **Import**. Done — Master recognized, zero typing.
- **Baked-in (zero-touch):** set a repo secret `MASTER_IDENTITY` to `{"k":"your-key","n":"Your Name","a":"about you"}` → rebuild → every install from that APK recognizes you automatically.
- Same warning as the Gemini key: anyone holding the card or APK can read it — keep both private.

## Project layout

```
android/                  # native app (this is the main product now)
  app/src/main/java/com/jarvis/app/
    frontend/screens/     # Compose UI: chat, HUD, share sheet, onboarding
    frontend/design/      # premium theme, config panel, assist island
    backend/brain/        # ViewModel + Gemini REST + router + offline tools + storage
    backend/system/       # wake service, bubble, accessibility bridge
    backend/device/       # device-command parsing (hardware-safe)
    backend/voice/        # voice loop, voiceprint, TTS helpers
    backend/data/         # vault DB, lists, reminders, changelog
    backend/net/          # GitHub API + encrypted token storage
  app/src/test/...        # unit tests (calculator, router)
  ...
.github/workflows/android.yml  # CI: tests + stable-signed release APK
.github/workflows/keycheck.yml  # CI: manual baked-key health check
renovate.json               # Renovate: grouped Gradle + Actions updates
```

## Dev notes

- Min SDK 26 (Android 8) · builds on JDK 17 + AGP 8.5.2 (CI does it)
 updating the changelog list too.

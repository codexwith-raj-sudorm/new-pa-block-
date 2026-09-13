# Jarvis 🤖 — Android app

Personal AI assistant as a **native Android app** (Kotlin + Compose).
No server, no hosting, no sleep — the app talks to Google Gemini straight from your phone.

![android](https://github.com/codexwith-raj-sudorm/new-pa-block-/actions/workflows/android.yml/badge.svg?branch=arena/01a08a6b-new-pa-block)

## Features (v4)

- 💬 Chat with Gemini + auto model discovery, 20-message context, on-device history
- 💬 Multi-chat: new / switch / delete chats with auto-titles
- 🧠 Memory manager: view/add/delete memories (or “remember …” in chat)
- 🎙️ Voice I/O: in-app mic (no Google popup/beeps — all streams muted around every listen) + "Hey Jarvis" wake word with a Stark-style arc-reactor HUD bubble over any app (rotating telemetry ring, pulsing core, grows with your voice) + 7 named voices (Jarvis + 3 male + 3 female, each with its own personality) with preview + mute toggle
- ⏰ Offline tools (no key needed): time, calculator, memory, reminders, device control
- 📝 Todos & notes: checkable lists ("add milk to my list", "done 2") + quick notes — all offline
- 🔋 Wake word + reminders survive reboot (auto re-arm) + battery-optimization prompt
- 🧲 Mini arc-reactor home-screen widget: tap to arm wake mode, tap while Jarvis speaks to interrupt
- 🆕 What's-new popup on every update + full update history in Settings
- 🌀 Stark HUD states: live waveform ring, color-coded modes, edge-dock, status ticker, interface chimes
- ⚙️ Settings: optional own key, preferred model, refresh models
- 🎨 Stark cinematic theme (obsidian + gold + cyan) + white-hot HUD speech state
- 💻 Code Terminal: fenced code renders in monospace blocks with Copy (TTS skips code)
- ⚡ Share Hub: system share target — summarize, ELI5, bug-hunt, translate any text
- 🔇 Device silence: “silence my phone” / “unsilence” via Do Not Disturb
- 🔁 Hands-free mode: mic re-opens after every reply (menu toggle)
- 🗄️ Memory Vault: facts in Room DB with 30-day auto-expiry + seamless migration
- 📊 Briefing card: battery, memory, storage, network + chat search + starter chips
- ↻ One-tap retry on failed replies + 📤 export any chat as text
- 🛠️ Everyday tools: alarms, timers, navigation, web search, play from YouTube
- 🔄 Unit + live currency converter, dice, coin, jokes, good-morning routine
- 🌦️ Keyless weather, 🔌 smart-home webhooks, 🕒 timestamped bubbles
- 🔔 Notification reader, ☀ 8 AM briefing, QS tile, shortcuts, Hindi mic, Voice Studio

## Install (phone, no PC needed)

1. Open this repo on GitHub → **Actions** tab → latest green `android` run
2. Download **jarvis-apk** under Artifacts (it's a `.zip` — unzip it)
3. Open the `.apk` → **Install** (allow "install unknown apps" if asked)
4. Open **Jarvis** and chat! 🎉 (A built-in default key is baked in — or tap ⚙️ to use your own key instead.)

Every push to this branch rebuilds the APK automatically.

## Built-in default key (owner setup)

The APK bakes in a locked default key from the `GEMINI_API_KEY` repo secret (never in git).
In ⚙️ Settings the built-in key can't be viewed, changed, removed or overridden —
but users can paste their own key in a separate section and switch to it.

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
    MainActivity.kt       # Compose UI: chat, bubbles, settings
    JarvisBrain.kt        # ViewModel + Gemini REST + offline tools + storage
  app/src/test/...        # unit tests (calculator, router)
  ...
.github/workflows/android.yml  # CI: tests + debug APK artifact
app.py, templates/        # Flask web version (legacy fallback, kept for reference)
```

## Dev notes

- Min SDK 26 (Android 8) · builds on JDK 17 + AGP 8.5.2 (CI does it)
- Key lives only on your device (app storage) — never in git
- Roadmap: voice I/O (native STT/TTS), reminder notifications, todos/notes screens

# Jarvis 🤖 — Android app

Personal AI assistant as a **native Android app** (Kotlin + Compose).
No server, no hosting, no sleep — the app talks to Google Gemini straight from your phone.

![android](https://github.com/codexwith-raj-sudorm/new-pa-block-/actions/workflows/android.yml/badge.svg?branch=arena/01a08a6b-new-pa-block)

## Features (v2)

- 💬 Chat with Gemini + auto model discovery, 20-message context, on-device history
- 💬 Multi-chat: new / switch / delete chats with auto-titles
- 🧠 Memory manager: view/add/delete memories (or “remember …” in chat)
- 🎙️ Voice I/O: mic input + spoken replies with mute toggle (top bar)
- ⏰ Offline tools (no key needed): time, calculator, memory
- ⚙️ Settings: optional own key, preferred model, refresh models

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

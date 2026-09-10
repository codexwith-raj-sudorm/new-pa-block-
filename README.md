# Jarvis 🤖 — Android app

Personal AI assistant as a **native Android app** (Kotlin + Compose).
No server, no hosting, no sleep — the app talks to Google Gemini straight from your phone.

![android](https://github.com/codexwith-raj-sudorm/new-pa-block-/actions/workflows/android.yml/badge.svg?branch=arena/01a08a6b-new-pa-block)

## Features (v1)

- 💬 Chat with Gemini (free API key, yours) + 20-message history, saved on-device
- 🔄 Model fallback chain (`gemini-2.5-flash-lite` → `2.5-flash` → `3-flash`) on quota errors
- ⏰ Offline tools (no key needed): time, calculator, `remember`/`recall` memory
- 🧠 Saved facts auto-injected into every chat — Jarvis knows you
- ⚙️ In-app Settings: paste key once, pick preferred model

## Install (phone, no PC needed)

1. Open this repo on GitHub → **Actions** tab → latest green `android` run
2. Download **jarvis-apk** under Artifacts (it's a `.zip` — unzip it)
3. Open the `.apk` → **Install** (allow "install unknown apps" if asked)
4. Open **Jarvis** → tap ⚙️ → paste your free Gemini key from `aistudio.google.com` → Save
5. Chat! 🎉

Every push to this branch rebuilds the APK automatically.

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

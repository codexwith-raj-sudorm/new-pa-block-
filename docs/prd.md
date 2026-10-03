# Jarvis PRD — v6.21

| Field | Value |
|---|---|
| Product | Jarvis — Personal AI Assistant (Android) |
| Package | `com.jarvis.app` |
| Platform | Android API 26+ (Android 8) · Kotlin + Compose |
| Current version | 6.21 (72) |
| Owner | Raj Thakur · West Bengal, IN |
| Status | Shipped — 262 JVM tests, stable signed updates |

## 1. Overview

Jarvis is a native Android assistant that chats with Google Gemini directly from the phone — no server, no hosting, no sleep. It blends a premium green-glass HUD with voice-first interaction: you can type, speak, or say "Hey Jarvis" from any screen and get a warm, witty, helpful reply.

Public/release APKs are BYOK: users paste their own free key from `aistudio.google.com` in Settings. Private/dev builds can inject owner convenience keys from environment secrets, but release builds keep those BuildConfig fields empty.

## 2. Target audience

- **Android power users** who want a customizable, sci-fi-themed assistant that replaces Google Assistant.
- **Privacy-minded users** who prefer on-device history, encrypted key storage, and optional offline tools.
- **Makers / developers** who use GitHub-by-voice, webhooks, and screen-context tools to get things done.

## 3. Principles & constraints

- **BYOK, no paid SDK in public APKs:** the app never ships a hard-coded paid key in git; public/release builds leave built-in Gemini/GitHub/Master secrets empty. Private/dev convenience keys are obfuscated only and must stay private. User keys always win.
- **On-device first:** chats, memories, lists, reminders live in Room / EncryptedSharedPrefs. Gemini is the only cloud call.
- **Hardware-safe:** no torch / brightness / Wi-Fi toggles — those requests get an honest refusal, never a hallucinated toggle.
- **Voice is a feature, not the only UI:** full chat UI exists; voice, wake word, and hands-free are additive.
- **Sir, always:** Jarvis addresses the owner as "sir" when a Master identity is present.

## 4. Key features (v6.21)

**Chat & memory**
- Gemini chat with auto model discovery, 20-message context, per-chat history, auto-titles, rename/search/delete, export & full backup.
- Code blocks as terminal panels (Copy / Share, TTS skips code).
- Memory vault (Room, 30-day expiry, 200 cap) — "remember that …" / "recall …" in chat or voice.

**Voice & wake**
- Fixed Priya voice (pitch 0.68, rate 0.93), beeps muted on every listen, sentence-chunked TTS with live HUD state.
- In-app mic (Hindi/English), hands-free continuous mode, tap-to-interrupt (status pill / reactor).
- "Hey Jarvis" wake word: foreground service, fuzzy matcher (jervis/davis), offline-first, time-boxed standby window (08:00–18:00, overnight OK), wake pop over lock screen.

**Ambient intelligence**
- Dynamic Island: pill drops from the camera cutout when standby arms, collapses to dot, bursts open on wake with haptics.
- Proactive reflexes: unprompted voice for low battery + incoming WhatsApp/Telegram/Gmail/SMS (toggle, cooldowns, DND/pocket/BT guardrails, Gemini persona + offline fallback).
- Neural-link onboarding gate (3-phase establish) + calibration tour; master voice guard + 3-sample voiceprint.

**Tools & integrations**
- Media router: "play X" opens Spotify / YouTube directly — app chooser with remember, web fallback, spoken handoff.
- Device commands: silence/DND, call, app launch (fuzzy), alarms/timers, navigation, web search, screenshot / "what's on my screen" / tap, battery, widgets, Quick Settings tile, shortcuts.
- Built-in skills (no key): time/date, calculator, unit/currency (live FX), weather (wttr.in), jokes, dice/coin, day-part greetings, notification reader, smart webhooks.

**System**
- In-place signed updates (chats/keys/permissions survive), boot + watchdog restore for wake/reminders, What's-new + changelog, permission health, offline-first where possible.

## 5. Non-goals

- No standalone launcher / home-screen replacement.
- No paid third-party SDKs bundled in the APK.
- No continuous screen polling — Assist/screen context only on demand.
- No hardware toggles, no background telemetry.

## 6. Success criteria

- Install from Actions → Artifacts → jarvis-apk → chat in <30s (or Master Card import → zero typing).
- Wake word triggers <1s, standby window respected, island + reflexes visible.
- "Play X" opens the chosen app directly; reminders, memories, and chats survive reboot and update.

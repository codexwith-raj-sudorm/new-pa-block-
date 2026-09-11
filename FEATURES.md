# JARVIS App — Reactor Feature List

> Last updated: v5.1 (Stark HUD) + local reactor-interrupt update.
> Everything below runs on-device except Gemini cloud calls for chat.

## 1. Core AI Chat
- Chat with Google Gemini (free AI Studio key; built-in key works out of the box)
- Multi-model with automatic fallback on quota errors
- Multiple chats: create, rename, search, per-chat delete (tap-again to confirm), clear-all
- Code blocks render as terminal panels with Copy + Share (shares with language fence)
- Retry on failed replies; message timestamps; auto chat titles
- Export current chat / full backup (chats, memories, todos, hooks, reminders as JSON)

## 2. Voice (Speech)
- Fixed voice: **Priya**, pitch **0.68**, rate **0.93** — identical in app + wake service
- Mic input with Hindi/English listening modes
- Hands-free continuous mode (listens again after every reply)
- Code fences, URLs, and symbols cleaned before speaking ("code snippet", "link")
- Sentence-chunked speech with live HUD speaking state
- ⚛️ **Tap the arc reactor to interrupt** — welcome core is tappable; a mini reactor appears in the header while speaking ("tap reactor to stop"); hands-free keeps listening after interrupt

## 3. Wake Word ("Hey Jarvis")
- Always-on foreground mic service with battery/optimization handling
- "Yes sir?" reply; once-per-day-part greeting (Good morning/afternoon/evening, Master Raj)
- Quick Settings tile to toggle wake; reactor widget tap to arm/disarm or interrupt speech
- Wake greetings spoken in the same Priya voice

## 4. Master Key System
- Install a master key → Jarvis recognizes you as Master (creator identity injected into every reply, tickers, greetings)
- Code-baked master: `JARVIS-RAJ-MASTER-77` / Raj Thakur / West Bengal, India — works on every install
- Shareable Master Card (`JARVIS-MASTER:` code) — import on any device
- Master section hidden in Settings — **tap the "Jarvis Settings" title 5× to unlock**

## 5. Memory
- "Remember that …" / "recall …" voice + text commands
- On-device Room vault (30-day auto-purge, 200-item cap)
- Stark privacy vault mirror (90-day auto-purge) — silent background store
- Memory viewer dialog in menu

## 6. Lists, Notes & Todos
- Todo lists + notes with check-off, add/remove (voice + UI)

## 7. Reminders & Alarms
- "Remind me in 10 minutes to …" / "at 5pm" / "tomorrow at 9am"
- Exact alarms with notification; cancel by voice or UI
- Quick-add field inside the Reminders dialog (no voice needed)
- Next reminder shown on the briefing widget with countdown

## 8. Briefing & Dashboard
- ⚡ Briefing dialog (battery, storage, network, quote)
- Daily 8 AM briefing notification (toggle)
- Briefing home-screen widget (time, date, battery, next reminder, tap-to-refresh)
- Stark dashboard on welcome screen: live temperature (wttr.in), battery, measured ping — tap ⟳ to refresh
- Pulsing golden brain-core arc reactor visual

## 9. Stark HUD Retheme
- Golden `StarkHeader` (JARVIS HUD + neural-link dot) replacing the old top bar
- `StarkMessageCard` chat bubbles (COMMAND // USER / NEURAL_RESP)
- `GoldenBrainCoreView` reactor, `StarkGoldenBubble` draggable bubble component
- `StarkBriefingDashboard` telemetry panel

## 10. Stark Hub (Share Intercept)
- Single "Jarvis" Android share target → opens Stark Hub screen
- PROCESS & INJECT sends shared text straight to Jarvis automatically

## 11. Stark Wake Widget
- Home-screen reactor widget: ACTIVE/STANDBY toggle persisted across reboots
- Toggle drives real wake-word listening (starts/stops the wake service)

## 12. Stark ID Lock (Biometric)
- Fingerprint/face gate screen ("Stark Neural Identity Check")
- Launchable from Settings → More → Stark ID lock

## 13. Hardware Voice Commands
- "Turn on/off the flashlight" (flash-capable camera auto-detected)
- "Silence my phone" / "sound on" (Do Not Disturb)
- "Open YouTube / launch maps / start camera …" (fuzzy app-name match)
- "Call mom" (opens dialer, never auto-calls), alarms, timers, navigation, web search, "play …", Wi-Fi panel, system settings
- Battery reader, emergency silence, app launcher (`StarkDeviceController`)

## 14. Smart Actions (Webhooks)
- Name + URL (+ GET/POST) hooks for Home Assistant, IFTTT, ESP devices
- Trigger by voice ("turn on bedroom light")

## 15. Built-in Skills (no key needed)
- Time/date, calculator, unit conversion, currency conversion (live FX)
- Weather + forecast (keyless wttr.in), jokes, coin flip, dice, day-part greetings
- Notification reader ("read my notifications" — opt-in listener)

## 16. System Integration
- Quick Settings tile (wake toggle), app shortcuts, boot receiver (restores wake + briefings)
- What's-new dialog after updates; onboarding checklist (mic, overlay, notifications)
- Slim 4-item menu (New chat, Memory, Lists, Voice); everything else in Settings → More
- 148 unit tests; Room + kapt; Material 3 dark HUD theme

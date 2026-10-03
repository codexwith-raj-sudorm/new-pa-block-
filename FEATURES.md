# JARVIS App — Reactor Feature List

> Last updated: v6.21.
> Hardware toggles (flashlight/brightness/Wi-Fi switch) are intentionally unsupported.
> Everything below runs on-device except Gemini cloud calls for chat.

## 1. Core AI Chat
- Chat with Google Gemini (private builds can inject a built-in key from `local.properties`)
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
- Tap the status pill (or the bubble) to interrupt mid-speech

## 3. Wake Word ("Hey Jarvis")
- Foreground mic service with battery/optimization handling + offline-first recognition
- Time-boxed standby window (default 08:00–18:00, overnight OK) with exact edge alarms
- Wake pop: Jarvis seizes the screen over the lock screen; headless command listen as fallback
- "Yes sir?" reply; once-per-day-part greeting (Good morning/afternoon/evening, Master Raj)
- Quick Settings tile to toggle wake; reactor widget tap to arm/disarm or interrupt speech

## 4. Private Identity
- Entering **Raj** in onboarding resolves local clearance as Admin
- Optional built-in Gemini/GitHub keys load from untracked `local.properties` or CI secrets
- Master Key controls remain in backend for compatibility, but the user-facing Settings panel is hidden
- Master voice guard + 3-sample voiceprint enrollment remain available for locked voice commands

## 5. Memory
- "Remember that …" / "recall …" voice + text commands
- On-device Room vault (30-day auto-purge, 200-item cap)
- Memory viewer dialog in the drawer

## 6. Lists, Notes & Todos
- Todo lists + notes with check-off, add/remove (voice + UI)

## 7. Reminders, Alarms & Scheduled Messages
- "Remind me in 10 minutes to …" / "at 5pm" / "tomorrow at 9am"
- Exact alarms with notification; cancel by voice or UI
- Quick-add field inside the Reminders dialog (no voice needed)
- Next reminder shown on the briefing widget with countdown
- Scheduled messages ("text mom I'll be late tomorrow at 9am") — SMS automatic, WhatsApp via tap

## 8. Briefing & Dashboard
- ⚡ Briefing dialog (battery, storage, network, quote)
- Daily 8 AM briefing notification (toggle)
- Briefing home-screen widget (time, date, battery, next reminder, tap-to-refresh)
- Welcome hero: live temperature, battery, measured ping — tap ⟳ to refresh

## 9. Premium Green-Glass HUD
- Edge-swipe drawer + floating header (menu, status pill, avatar) + top-right profile hub
- Unified glass dialogs (chats, identity, lists, config) with neon accents
- Living swirl core, waveform ring, color-coded modes, status ticker, interface chimes
- Orbital app icon + matching widget art; hold-summon floats with zero dimming

## 10. Share Hub (Share Intercept)
- Single "Jarvis" Android share target → floating sheet over any app
- Summarize text, describe/read shared images, inject to chat

## 11. Widgets
- Reactor widget: ACTIVE/STANDBY toggle persisted across reboots, drives real wake listening
- Briefing widget: time, date, battery, next reminder, tap-to-refresh

## 12. Standby Window & Dynamic Island
- Standby window with start/end pickers in Settings (exact alarms, boot + watchdog aware)
- Stealth IMPORTANCE_MIN notification showing the armed window
- Dynamic Island: black pill drops from the camera cutout on arm, dot after 5s, wake strike with haptics

## 13. Device Commands & Media Router
- "Silence my phone" / "sound on" (Do Not Disturb)
- "Open YouTube / launch maps / start camera …" (fuzzy app-name match)
- "Call mom" (places the call directly), alarms, timers, navigation, web search, Wi-Fi panel, system settings
- Screen control ("take a screenshot", "what's on my screen", "tap …"), battery reader, emergency silence
- Media router: "play …" opens Spotify/YouTube directly — app chooser with remember, web fallback, spoken handoff

## 14. Smart Actions (Webhooks)
- Name + URL (+ GET/POST) hooks for Home Assistant, IFTTT, ESP devices
- Trigger by voice ("turn on bedroom light")

## 15. Proactive Reflexes & Built-in Skills (no key needed)
- Reflexes speak unprompted: low battery + WhatsApp/Telegram/Gmail/SMS arrivals (toggle in Settings)
- Gemini persona lines with offline fallback; music ducks; DND/cooldown/call/pocket guardrails
- Time/date, calculator, unit conversion, currency conversion (live FX)
- Weather + forecast (keyless wttr.in), jokes, coin flip, dice, day-part greetings
- Notification reader ("read my notifications" — opt-in listener)

## 16. System Integration
- Neural-link onboarding gate (3-phase establish) + permission checklist + calibration tour
- Quick Settings tile (wake toggle), app shortcuts, boot receiver (restores wake + briefings)
- What's-new dialog after updates; in-place updates keep chats/keys/permissions
- 262 unit tests across 43 files; Room + kapt; Material 3 dark HUD theme

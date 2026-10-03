# Jarvis Build Phases — v6.21 actuals

> What was *planned* vs what *shipped*. Early speculative phases (voice-only overlay) were superseded by the chat-first product that users actually wanted. This file tracks the real milestones as committed.

## Shipped phases (condensed)

| Phase | What shipped | Version |
|---|---|---|
| P0–P1 Foundation | Fork `flask-gemini-chatbot` → Jarvify, add Android app (Kotlin+Compose, direct Gemini REST, CI APK) | 1.0–1.3 |
| P2 Voice v1 | In-app mic (beeps muted), male voice + picker, wake word "Hey Jarvis", shell around Google popup fix, multi-chat + memory manager | 1.3–2.0 |
| P3 Lists & HUD | Todos/notes/lists, reminders+alarms, arc-reactor bubble HUD, widgets, bubble visualizer + color states | 2.0–2.5 |
| P4 Polish & vault | Cinematic theme, code terminal, share hub, Room vault (30-day), briefing card, retry/share, everyday tools (weather, converters) | 2.6–3.3 |
| P5 Webhooks & shell | Smart webhooks, QS tile + shortcuts, notification reader, Hindi mic, screen control, scheduled messages, voiceprint guard, image gen | 3.4–5.0 |
| P6 Permissions & debut | Floating share sheet, empty fresh chats, instant-mic wake + fuzzy match, orbital icon, encrypted secrets, bounded chat, master-gate | 5.9–6.6 |
| P7 Theming & debut | Honest hardware refusals, owner-card, onboarding/calibration, chrome polish, dockable bubble, clear summon, island+dim, stable identity | 6.7–6.11 |
| P8 Private/dev brain & signing | Private/dev master unlocks optional built-in brain, sir everywhere, stable signing (in-place updates) + Renovate, hygiene restack | 6.12–6.15 |
| P9 Ambient systems | Gesture nav (§9) | 6.16 |
| P10 Ambient systems | Time-boxed standby (§6) | 6.17 |
| P11 Ambient systems | Dynamic Island (§8) | 6.18 |
| P12 Ambient systems | Proactive reflexes (§7) | 6.19 |
| P13 Onboarding & media | Neural-link gate (§10) | 6.20 |
| P14 Onboarding & media | Zero-API media router (§11) | 6.21 |

## Architecture sections (§§1–11)

All 11 sections of `architecture.md` are implemented at max potential:

1. High-level architecture — shipped
2. Directory & folder structure — shipped (see scaffold.md)
3. Technology stack — shipped
4. Execution data flow — shipped
5. User authentication — local-profile onboarding (no OAuth, per house rule) + neural-link gate
6. Tutorial/spotlight — calibration tour (spotlight + typewriter)
7. Standby window — time-boxed listener + stealth service + wake pop
8. Proactive intelligence — battery + message reflexes
9. Gesture navigation — drawer + floating header + profile hub
10. Google auth plan — adapted as local neural-link gate (OAuthspec absorbed as Jarvis)
11. Playback plan — zero-API media router (Spotify/YouTube, app chooser + remember)

## Next (proposed)

- Post-v6.21 polish: instrumentation for interrupt rate, island placement on foldables, media router analytics (local-only).
- If re-adding VoiceInteractionService assist hook, gate it behind a "system assist" toggle — current product does not need SYSTEM_ALERT_WINDOW.

## Historical note

Earlier `phases.md` described a 6-phase voice-only `VoiceInteractionService` with a 3D neural sphere and no chat UI. That direction was intentionally abandoned after v1 — users wanted a real chat app with voice as an additive. The old phases are kept in git history for reference but no longer describe the product.

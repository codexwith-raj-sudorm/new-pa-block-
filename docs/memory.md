# Jarvis Memory — Project State (v6.21)

> Source of truth for where the project is *right now*. Update this file with every release.

| Field | Value |
|---|---|
| Version | 6.21 (72) — 2026-10-01 |
| Package | `com.jarvis.app` · Min 26 · Target 34 · AGP 8.5.2 · JDK 17 |
| Tests | 262 across 43 files (JVM-pure, via `:app:testDebugUnitTest`) |
| Branch | `arena/01a08a6b-new-pa-block` (from `568d725`) · CI `android.yml` + `keycheck.yml` |

## Current state

- **Shipped through §11:** gesture nav (§9), time-boxed standby (§6), standby island (§8), proactive reflexes (§7), neural-link auth gate (§10), zero-API media router (§11) — all 11 architecture sections max-potential and tested.
- **Docs renamed:** `docs/*_md → docs/*.md` + index at `docs/README.md` (AEGIS → Jarvis noted).
- **READMEs refreshed:** `README.md` + `FEATURES.md` for v6.21.

## Active work

- Request: "rewrite all files to make them latest, reorder folders, make commits neat, then push+build."
- Docs refresh in progress (this file + prd/phases/rules/scaffold). Next: history tidy → push → CI build.

## Completed milestones (recent)

- 6.21 Media router (play commands → Spotify/YouTube, chooser with remember)
- 6.20 Neural-link gate (glass auth module, 3-phase establish, clearance pill)
- 6.19 Proactive reflexes (battery + message triggers, persona voice, guardrails)
- 6.18 Dynamic Island (cutout-tethered pill → dot, wake strike)
- 6.17 Time-boxed standby (window alarms, stealth service, wake pop over lock)
- 6.16 Gesture nav (drawer + floating header + profile hub)
- 6.15 Restack (hygiene + design purge + glass + compact island)
- 6.14 Stable signing (in-place updates) + Renovate
- 6.13 Sir + any master key unlocks baked brain

See `android/app/src/main/java/com/jarvis/app/backend/data/Changelog.kt` for the full 1.0→6.21 history (newest first).

## Technical context

- **AI:** direct Gemini REST (`AiProviders` + `GenImage`), auto model discovery, fallback, per-IP cooldown; OpenAI-compatible endpoints optional.
- **Voice:** `VoiceLoop` + `VoiceCapture` + `VoicePrintDsp` (tiny speaker-ID vote), Priya TTS, `SoundMuter` for beeps; wake via `WakeService` (IMPORTANCE_MIN stealth channel).
- **HUD:** green-glass theme (`HudTheme`, `FluidJarvisTheme`, `PremiumHome`, `ArcCoreHud`, `WakeHarmonic`, `AssistIsland`), edge-swipe `JarvisNav`, `JarvisAuth`, `JarvisMedia`.
- **System:** `StandbyWindow`/`StandbyIsland`/`Proactive`/`NotifReader`/`BootReceiver`/`AssistMode`/`ScreenshotService`, widgets (`ReactorWidget`, `BriefingWidget`).

## State machine (simplified)

| State | Trigger | Visual |
|---|---|---|
| Idle | app open, no listen | swirl core + dashboard |
| Listening | mic / wake word | waveform ring, green hot |
| Thinking | Gemini call | amber shimmer |
| Speaking | TTS chunk | pill speaking, reactor hint |
| Standby-armed | window active, wake on | island pill/dot |

## Notes for next agent

- This session fixes `latest_prd` + renames all `*_md` → `*.md`. Don't recreate `*_md` paths.
- Stack is 8 commits atop `c629c01` (all unpushed). History rewrite should preserve co-author trailer on feature commits.
- No Android SDK in this sandbox — CI is the verifier (`gradle :app:testDebugUnitTest` then assemble).

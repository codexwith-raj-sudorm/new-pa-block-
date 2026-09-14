# PRD: Arc Reactor System (JARVIS App)

| Field | Value |
|---|---|
| Version | 1.0 |
| Status | Implemented (v5.1 + local reactor-interrupt update, unreleased) |
| Owner | Raj Thakur |
| Components | `GoldenBrainCoreView`, `StarkGoldenBubble`, `ReactorWidget`, `StarkWidgetProvider`, header mini-reactor |

## 1. Overview

The Arc Reactor is JARVIS's visual + interactive core: a pulsing golden arc-reactor rendered in
Compose that (a) gives the app its Stark identity, and (b) acts as the universal **"stop talking"**
control. Whenever JARVIS is speaking, a reactor is on screen; tapping it interrupts speech
immediately — including in hands-free mode, which previously had no stop control at all.

## 2. Goals / Non-Goals

**Goals**
- G1: A signature reactor visual (pulse + orbit) on the welcome screen.
- G2: Tap-reactor-to-interrupt works in every mode (chat, voice, hands-free, wake).
- G3: A speaking indicator that is visible exactly while TTS audio is playing.
- G4: Home-screen widgets reflecting/arming wake state.

**Non-Goals**
- NG1: Floating overlay over other apps (bubble supports drag, but no overlay service).
- NG2: Reactor-driven actions beyond interrupt (no long-press menus, no volume control).
- NG3: Custom reactor skins/themes.

## 3. Users & Use Cases

- **U1 — Hands-free user:** JARVIS reads a long reply; user taps the header mini-reactor to cut
  him off and immediately speaks the next command (mic stays open).
- **U2 — Chat user:** taps the big welcome-core reactor to stop speech while reading.
- **U3 — Widget user:** glances at ACTIVE/STANDBY; taps to arm/disarm wake-word listening.

## 4. Functional Requirements

### 4.1 GoldenBrainCoreView (in-app reactor)
- FR-1: Renders a 96dp reactor: outer gold ring, dashed mid ring, orbiting spark (4s rotation),
  breathing radial core (0.85–1.15 scale, 1s reverse pulse).
- FR-2: Accepts `modifier` + `onClick: () -> Unit = {}`; whole circle is the tap target
  (clipped to circle, ripple via `clickable`).
- FR-3: Zero business logic; pure presentational composable (no tests required).

### 4.2 Welcome-screen reactor
- FR-4: Shown centered above the Stark dashboard when `messages.size <= 1 && !busy`.
- FR-5: Tap → `vm.interruptSpeech()`.

### 4.3 Header mini-reactor (speaking indicator)
- FR-6: Shown in the header utility row **iff** `HudStateBus.state.speaking == true`.
- FR-7: 40dp `GoldenBrainCoreView` + "tap reactor to stop" hint; tap → `onInterrupt`
  (`vm::interruptSpeech`).
- FR-8: Appears/disappears with speech; no layout shift elsewhere (row wraps content).

### 4.4 Interrupt semantics (`interruptSpeech()`)
- FR-9: Stops TTS immediately (`tts.stop()`), clears `SpeechState.speaking`,
  posts `HudStateBus.update(speaking = false)` synchronously (UI resets without waiting
  for the utterance callback).
- FR-10: In hands-free mode (`continuous && ttsOn`), resumes listening right after the
  stop so the conversation continues mic-open.
- FR-11: Safe to call when silent (no-op, no crash).

### 4.5 ReactorWidget (legacy home-screen widget)
- FR-12: Tap → `ACTION_WIDGET_TAP` → MainActivity: if speaking, interrupt; else toggle wake.
- FR-13: Bright (255) when `WakeService.isRunning`, dim (110) otherwise.

### 4.6 StarkWidgetProvider (new home-screen widget)
- FR-14: Reactor icon + status label (`starkWidgetLabel`, pure + unit-tested):
  `true → "JARVIS: ACTIVE"`, `false → "STANDBY"`.
- FR-15: Toggle persisted in `SharedPreferences("stark_widget")` — survives reboot.
- FR-16: Toggle drives real wake state via `ACTION_STARK_WAKE` → MainActivity →
  `vm.setWakeEnabled(on)` (foreground-safe; no background service start from the widget).

### 4.7 StarkGoldenBubble (component, currently unused)
- FR-17: 72dp draggable reactor bubble with pulse + 5s orbit; exposes `onClick` and
  `onPositionChanged`. Reserved for future overlay/in-app mic use.

## 5. UX / States

| State | Welcome core | Header mini-reactor | Widgets |
|---|---|---|---|
| Idle, empty chat | Visible, tappable (no-op) | Hidden | Label per prefs |
| Speaking | Hidden (chat open) | **Visible**, tappable → stop | Reactor bright |
| Speaking, empty chat | Visible → stop | Visible → stop | Reactor bright |
| Listening/thinking | As idle | Hidden | Unchanged |

## 6. Edge Cases & Error Handling
- E1: Tap during TTS engine init/teardown → `runCatching`/try-catch, silent no-op.
- E2: `HudStateBus` stuck `speaking=true` (missed callback) → interrupt forces `false` (FR-9).
- E3: Widget tap while app process dead → MainActivity cold-starts, intent still honored.
- E4: After reboot, Stark label restores from prefs; wake restores from its own pref via
  `BootReceiver` (both written together on every toggle, so they agree).
- E5: Accessibility: reactor tap target ≥ 40dp; header hint is text (screen-reader visible).

## 7. Technical Notes
- Animations: `rememberInfiniteTransition` (pulse `tween 800–1000ms Reverse`, orbit
  `tween 4000–5000ms Restart`); Canvas rings + `dashPathEffect`; radial-gradient core.
- Speaking source of truth: `HudStateBus.state.speaking`, fed by TTS utterance callbacks
  (`onStart/onDone/onError`) in both `JarvisViewModel` and `WakeService`.
- No new permissions, no new dependencies, no DB changes for this system.

## 8. Test Plan
- Unit (JVM): `starkWidgetLabel` true/false (`StarkLogicTest`) — done.
- Manual: (1) send long reply → tap header reactor mid-speech → audio stops, hint hides;
  (2) hands-free on → interrupt → mic reopens; (3) widget toggle → label flips, survives
  reboot; (4) reactor widget tap while speaking → interrupts.
- Not unit-testable (Compose/Android): ring rendering, tap wiring, widget RemoteViews.

## 9. Metrics (future)
- Interrupt taps per session; % of speeches interrupted (proxy for reply-length tuning).

## 10. Future Work (out of scope)
- Floating overlay bubble over other apps (needs overlay service + permission flow).
- Reactor color states (blue idle / red speaking / green listening).
- Haptic tick on interrupt; voice command "stop" / "shut up" (VAD-based barge-in).

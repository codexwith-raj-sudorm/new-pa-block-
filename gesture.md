# Gesture Control Plan

## 1. Goal

Add an optional, user-controlled gesture mode for Jarvis/Edith that uses the device camera to track a hand and translates deliberate gestures into pointer movement and accessibility actions.

The first release should prioritize predictable behavior, privacy, battery life, and easy recovery from false positives.

## 2. Guardrails and Non-Goals

- Camera access must require the normal Android runtime permission.
- Gesture mode must be started and stopped by an explicit user action or clearly visible voice command.
- The camera must not run invisibly or silently in the background.
- Do not suppress Android camera/microphone privacy indicators.
- Do not upload camera frames or landmarks to Gemini or another remote service.
- Do not execute arbitrary shell, Shizuku, or root commands from a gesture.
- Accessibility actions must be limited to an explicit allowlist and must respect the user's enabled AccessibilityService state.
- Destructive actions such as deleting data, sending messages, or changing device settings require a second confirmation.
- When gesture mode is stopped, CameraX, MediaPipe, executors, and overlays must be released promptly.

## 3. User Experience

### Entry and status

1. The user opens **Gesture Control** from the app settings or a clearly labelled action.
2. Jarvis explains that the camera will be used locally for hand tracking and requests `CAMERA` permission.
3. The user taps **Start gesture control**.
4. The screen displays a visible status indicator: `Gesture control active`.
5. A persistent, low-importance notification identifies the active camera session and provides **Stop**.
6. The user can stop the session from the app, notification, quick action, or the hardware/back flow.

### Initial gesture set

Keep the first version deliberately small:

- **Open palm:** move the pointer without activating a click.
- **Closed fist:** click once at the current pointer position.
- **Thumbs up:** confirm a pending non-destructive action.
- **Victory:** return to the home screen only when the action is enabled in settings.
- **No hand / low confidence:** pause pointer updates and show a neutral state.

Every discrete gesture needs a debounce interval and cooldown so one physical pose cannot generate repeated actions.

## 4. Architecture

```text
GestureSettings / MainActivity
            |
            v
GestureSessionController
            |
   +--------+---------+
   |                  |
CameraXFrameSource  GestureRecognizer
   |                  |
   +--------+---------+
            v
      LandmarkTracker
            |
            v
      GestureInterpreter
            |
            v
     ActionPolicyGate
            |
            v
AccessibilityActionExecutor
```

### 4.1 GestureSessionController

Own the session lifecycle and expose a small state machine:

```text
OFF -> REQUESTING_PERMISSION -> STARTING -> ACTIVE
                                      \-> ERROR
ACTIVE -> PAUSED -> ACTIVE
ACTIVE -> STOPPING -> OFF
```

Responsibilities:

- Prevent duplicate camera sessions.
- Coordinate permission checks and user-visible errors.
- Start and stop the frame source and recognizer together.
- Publish state to Compose UI and the notification.
- Stop automatically when the activity leaves the configured session scope unless the user explicitly selected an allowed foreground mode.

### 4.2 CameraXFrameSource

Use CameraX `ImageAnalysis` as the first camera integration.

Recommended behavior:

- Bind only an `ImageAnalysis` use case unless a camera preview is needed for calibration.
- Use `STRATEGY_KEEP_ONLY_LATEST` to avoid a growing frame queue.
- Run analysis on a dedicated executor, never the main thread.
- Close every `ImageProxy` in a `finally` block.
- Use a monotonic timestamp for `recognizeAsync`.
- Apply the front-camera mirror consistently to the pointer mapping and preview.
- Stop the analyzer before releasing the camera provider.
- Cap analysis to a measured rate, initially around 15 FPS, and tune after profiling.

### 4.3 MediaPipe GestureRecognizer

Use the official MediaPipe Tasks Vision Android package and pin the dependency to a tested version.

Model setup:

- Store the licensed `gesture_recognizer.task` model in `app/src/main/assets/`.
- Load it through `BaseOptions` from the application asset manager.
- Use `RunningMode.LIVE_STREAM` for asynchronous CameraX callbacks.
- Configure a confidence threshold for detection, presence, and tracking.
- Keep model creation and closure on the same controlled lifecycle path.
- Treat recognizer errors as recoverable session errors; do not spin in a tight restart loop.

The recognizer result should be reduced to a small immutable frame model:

```kotlin
data class HandFrame(
    val timestampMs: Long,
    val indexTipX: Float?,
    val indexTipY: Float?,
    val label: String?,
    val confidence: Float,
    val handPresent: Boolean,
)
```

Do not retain camera buffers after the callback. Only the minimum landmark data needed for the current frame should cross into the tracking layer.

## 5. Pointer Mapping

MediaPipe landmarks are normalized to the input image. Convert them into the active display or app-window coordinate space only after accounting for:

- Camera orientation and rotation.
- Front-camera mirroring.
- Display size and density.
- System bars, cutouts, and app window insets.
- Optional calibration margins to make edge movement easier.

Suggested pipeline:

```text
raw landmark
  -> mirror/rotate
  -> clamp to [0.0, 1.0]
  -> calibration transform
  -> exponential moving average
  -> dead-zone check
  -> screen coordinate
```

Start with an exponential moving average rather than a Kalman filter:

```text
smoothed = alpha * current + (1 - alpha) * previous
```

Use a configurable `alpha`, a small movement dead zone, and a maximum per-frame movement clamp. Reset the filter when the hand disappears so the pointer does not jump when tracking resumes.

## 6. Gesture Interpretation

The interpreter converts a stream of classified frames into stable events.

Required rules:

- Ignore classifications below the configured confidence threshold.
- Require a gesture to remain stable for several frames before accepting it.
- Emit one event on the rising edge, not once per frame.
- Require a neutral or different pose before accepting the same gesture again.
- Enforce a per-action cooldown.
- Cancel a pending action if tracking becomes unreliable.

Example event model:

```kotlin
sealed interface GestureEvent {
    data class PointerMoved(val x: Float, val y: Float) : GestureEvent
    data object Click : GestureEvent
    data object Confirm : GestureEvent
    data object Home : GestureEvent
}
```

Keep classification separate from policy. The interpreter may recognize `Home`, but the policy layer decides whether that action is enabled and whether confirmation is required.

## 7. Accessibility Execution Layer

`JarvisAccessService` should be the only component allowed to dispatch device-wide touch gestures.

Implementation rules:

- Verify that the service is enabled before attempting an action.
- Verify that the target coordinate is inside the current display bounds.
- Use a short, bounded `GestureDescription` stroke for a click.
- Reject stale events using their timestamp and session identifier.
- Serialize actions so two gestures cannot dispatch simultaneously.
- Report success or failure to the visible gesture status UI.
- Never use gesture input to grant permissions, bypass lock screens, or modify security settings.
- Map global actions through an explicit settings allowlist, disabled by default for the first build.

For the first implementation, pointer rendering and click execution should be limited to the app's own window. Device-wide accessibility control can be enabled only after the in-app flow, permission explanation, and recovery path are tested.

## 8. Feedback and Recovery

Provide visible feedback for:

- Camera permission missing.
- Accessibility permission missing.
- No hand detected.
- Low recognition confidence.
- Gesture mode paused because another app owns the camera.
- Executor or recognizer failure.
- Action rejected by policy.

The UI should include a large **Stop** control and a short explanation of what is active. A crash, camera disconnect, activity destruction, or process restart must leave gesture mode off unless the user explicitly opted into a supported foreground session.

## 9. Battery and Lifecycle

- Do not start CameraX from boot or as an invisible background listener.
- Stop the session when the user disables gesture mode or the allowed session ends.
- Release the recognizer, camera provider, executor, and overlay in `onStop`/`onDestroy` paths as appropriate.
- Avoid holding a wake lock for camera gesture control.
- Use frame dropping rather than queueing old frames.
- Record only aggregate diagnostics such as frame rate, inference duration, and dropped-frame count.
- Never persist raw frames, audio, or hand images by default.

## 10. Implementation Phases

### Phase 1 — Contracts and settings

- Add a `GestureSettings` model with enabled state, confidence threshold, smoothing value, and action allowlist.
- Add session state and user-facing status strings.
- Add unit tests for settings defaults and state transitions.

### Phase 2 — CameraX source

- Add the CameraX dependencies.
- Request `CAMERA` permission from a visible activity.
- Build `ImageAnalysis` with keep-latest backpressure.
- Add frame conversion and guaranteed `ImageProxy` closure.
- Test start, stop, rotation, camera denial, and camera-busy behavior.

### Phase 3 — MediaPipe recognition

- Add the official Tasks Vision dependency.
- Add the licensed model asset outside source control if repository policy requires it.
- Implement `LIVE_STREAM` recognition and immutable `HandFrame` output.
- Add recognizer lifecycle and error handling.
- Measure inference time and dropped frames on a real device.

### Phase 4 — Tracking and interpretation

- Implement mirroring, rotation, calibration, EMA smoothing, and dead zones.
- Implement stable-pose detection, rising-edge events, and cooldowns.
- Add deterministic unit tests using recorded landmark fixtures, not camera recordings.

### Phase 5 — In-app pointer and actions

- Render a pointer only while the session is visibly active.
- Add click execution inside the app window.
- Add the accessibility permission explanation and service-state checks.
- Keep global actions disabled until the in-app flow is reliable.

### Phase 6 — Hardening and release review

- Test permission denial and revocation.
- Test activity rotation, backgrounding, screen lock, camera interruption, and process death.
- Verify no raw camera data leaves the device or is written to disk.
- Verify stop controls release all resources.
- Profile battery and thermal impact for a 15-minute and 60-minute session.
- Document supported Android versions and known OEM differences.

## 11. Acceptance Criteria

- Gesture mode cannot start without explicit camera permission.
- The user can always see when gesture mode is active and can stop it immediately.
- No camera frame or landmark is sent to a network endpoint.
- Every analyzed frame is closed exactly once.
- Pointer movement is stable and does not jump after temporary tracking loss.
- One closed fist produces at most one click per cooldown interval.
- Low-confidence or stale frames produce no action.
- Accessibility actions are rejected when the service is disabled or the action is not allowlisted.
- Stopping the session releases CameraX, MediaPipe, executor, and UI resources.
- Unit tests cover mapping, smoothing, debouncing, policy checks, and lifecycle transitions.
- Device testing covers camera denial, rotation, backgrounding, lock screen, and camera contention.

## 12. Open Decisions

1. Should the first release support pointer control only inside Jarvis, or should it also support the user's explicitly enabled AccessibilityService?
2. Which gestures should remain enabled by default, and should the Victory-to-Home action stay disabled until the user opts in?
3. Should calibration be a four-point guided flow or a simple sensitivity/margin slider?
4. Which minimum Android API and device camera configurations should be part of the supported matrix?

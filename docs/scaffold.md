# Jarvis Scaffold — v6.21 actual layout

> The real module layout. This replaces the early VoiceInteractionService scaffold that never shipped — the current product is a standard Activity + Compose app with a foreground wake service.

## Top-level

```
new-pa-block-/
├── android/                  # Gradle project (the product)
│   ├── build.gradle          # plugin versions only (AGP 8.5.2, Kotlin 2.0.20)
│   ├── settings.gradle       # include(":app")
│   ├── gradle.properties     # 2g heap, AndroidX, official code style
│   └── app/
│       ├── build.gradle      # namespace com.jarvis.app, SDK 26/34, versionCode 72 (6.21)
│       │                     # BuildConfig: DEFAULT_GEMINI_KEY / DEFAULT_MASTER / DEFAULT_GITHUB_TOKEN
│       │                     # signingConfig release (keystore.p12 + PKCS12)
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/          # drawable, layout, mipmap-anydpi-v26, values, xml
│           └── java/com/jarvis/app/
├── docs/                     # product specs + inline HTML mockups (see docs/README.md)
├── .github/workflows/        # android.yml (tests+APK) · keycheck.yml (manual)
├── FEATURES.md               # full feature list
├── renovate.json             # grouped Gradle + Actions
├── README.md
└── LICENSE
```

## Source modules (100 .kt files, 43 test files)

```
backend/ai/           # AiProviders (Gemini + OpenAI-compat), GenImage, VoicePersonas
backend/brain/        # JarvisBrain (ViewModel + router + changelog glue), Playback (media router)
backend/data/         # Changelog, JarvisVault (Encrypted prefs), Lists, MasterCore,
                      # Reminders, SchedMsg, VaultDb + StarkVaultDb (Room)
backend/device/       # DeviceControl, StarkDeviceController, SoundMuter, StarkSounds
backend/net/          # Github (API + token storage)
backend/system/       # AssistMode, BootReceiver, BriefingReceiver, BubbleLevelBus,
                      # HudState, JarvisAccess, NotifReader, Proactive, ReminderReceiver,
                      # SchedMsgReceiver, ScreenshotService, SharedVm, Standby,
                      # StandbyIsland, StandbyWindow, WakeService, WakeTile
backend/voice/        # VoiceCapture, VoiceLoop, VoicePrintDsp
frontend/design/      # ArcCoreHud, AssistIsland, ConfigPanel, FluidJarvisTheme,
                      # HeaderMiniReactor, HudTheme, JarvisAuth (§10), JarvisMedia (§11),
                      # JarvisNav (§9), PremiumHome, WakeHarmonic
frontend/screens/     # MainActivity, AssistActivity, ShotActivity, StarkShareActivity, WakeHudActivity
frontend/widgets/     # BriefingWidget, ReactorWidget, StarkWidgetProvider
```

## Key runtime wiring

- **App entry:** `MainActivity` hosts Compose `MainScreen` — drawer (`JarvisNav`), floating header, profile hub, chat, voice, standby, island, media chooser, auth gate.
- **Brain:** `JarvisBrain` is the `AndroidViewModel` — holds chats, memories, lists, reminders, wake/standby/proactive/media state, commit-per-release setters, `buildSystem()` prompt.
- **Wake:** `WakeService` (IMPORTANCE_MIN `jarvis_standby` channel) + `StandbyWindow` alarms + `BootReceiver` + watchdog + `BubbleLevelBus` for island waveform.
- **Proactive:** `Proactive` background receiver + `NotifReader` hook → `fireProactive` (main-thread, cooldown, DND/call/speech/busy/pocket guardrails).
- **Media:** `Playback` pure router (`parsePlayMedia`, `pickMediaPackage`) → `JarvisBrain.handleMediaCommand` → `MEDIA_PLAY_FROM_SEARCH` / `ACTION_SEARCH` intents, spoken handoff + 800ms beat.

## Build & test

- CI runs `gradle clean :app:testDebugUnitTest --console=plain` (all 262 tests must be JVM-pure), then assembles Release if `ANDROID_KEYSTORE_B64` exists else Debug, uploading `jarvis.apk`.
- Local builds need no SDK in this sandbox — CI is the verifier.
- Version bump = `android/app/build.gradle` `versionCode`/`versionName` + prepend `Changelog.kt` entry + `ChangelogTest` size guard.
```


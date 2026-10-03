# Jarvis House Rules — v6.21

> How we work on Jarvis. Violating these has broken builds before — read before you change anything.

## 1. Product integrity

- **BYOK, no paid SDK in git/public APKs:** never commit a real Gemini / OpenAI / GitHub token. Public/release `BuildConfig` secret fields stay empty. Private/dev builds may inject `GEMINI_API_KEY`, `GH_READ_TOKEN`, `MASTER_IDENTITY`, and `MASTER_KEY` from the local/CI environment, lightly obfuscated only — warn in README.
- **Encrypted storage:** user keys + master key live in `EncryptedSharedPreferences` (see `JarvisVault` / `MasterCore`). No plaintext prefs.
- **Hardware-safe refusals:** never actually toggle torch/brightness/Wi-Fi. Reply honestly that it's unsupported (tested in `DeviceTest` / `StarkLogicTest`).
- **Sir, always:** when a Master identity is installed, address the owner as "sir" in greetings, tickers, and spoken replies.

## 2. Voice & wake discipline

- **Beep hygiene:** every mic open must be wrapped by `SoundMuter` (all streams muted) — or users hear the Google beep.
- **Priya is locked:** pitch 0.68, rate 0.93 — same in app and `WakeService`. Don't expose a voice picker.
- **Wake hygiene:** `WakeService` channel is `jarvis_standby` (IMPORTANCE_MIN), `PARTIAL_WAKE_LOCK`, `EXTRA_PREFER_OFFLINE`, `showWhenLocked`+`turnScreenOn` for the pop. Watchdog + `BootReceiver` must honor the standby window.
- **Never store a large local model:** one ~5 MB speaker-ID model is allowed; Porcupine/Vosk/Whisper are out (house `keep-system` vote).

## 3. UI rules

- **No AEGIS strings:** the product is **Jarvis** — absorb any AEGIS mockup as Jarvis (design_md says AEGIS in places, ship as Jarvis).
- **HUD only — but chat exists:** green-glass theme (`HudTheme` + `PremiumHome`), no second design skin. Keep the 4-item menu (New chat, Memory, Lists, Voice); everything else goes to Settings → More.
- **Island is compact:** one small pill, chats flow around it — nothing hidden beneath (house `compact-pill` vote).
- **Hold-summon:** zero dimming, island floats over the visible screen.

## 4. Engineering hygiene

- **Tests before push:** `gradle :app:testDebugUnitTest` (262 tests) + keep `android/app/src/test` pure (no `android.*` imports). Add a test with every feature.
- **Changelog is the source of truth:** `backend/data/Changelog.kt` `CHANGELOG` list (newest first) powers "What's new" + `ChangelogTest`. Prepend, never append.
- **Docs live in `docs/*.md`:** specs say `*_md` historically — current paths are `docs/*.md` (see `docs/README.md`). Don't recreate `*_md`.
- **Don't push without asking:** prepare/commit locally, push only on approval — but this session explicitly asks for push+build after the reorg.
- **Rebase discipline:** never parallel-edit the same file (proven race in §6 that lost 15+ edits). Use one sequential Python assert-then-write script per file set.

## 5. Master key discipline

- **Owner identity:** `MASTER_IDENTITY` JSON `{"k":"key","n":"name","a":"about"}` and optional `MASTER_KEY` are private/dev build inputs only. Public/release builds ship empty built-in Gemini/GitHub/Master secrets; users paste their own keys.
- **Cards travel:** `JARVIS-MASTER:` cards import on any device; same leak warning as Gemini keys.
- **Updates are in-place:** stable signing (`keystore.p12` from `ANDROID_KEYSTORE_B64`) — new APKs must not lose chats/keys/permissions.

## 6. Git & CI

- **Branch is fixed:** `arena/01a08a6b-new-pa-block` — never switch or push elsewhere (Arena tracks this session).
- **Commits:** conventional style (`feat:`, `chore:`, `docs:`, `§N … (code/name)`), co-author trailer `arena-agent` on feature commits. Keep history neat — squash WIPs before push.
- **CI:** `android.yml` (tests → assemble signed release or debug fallback) + `keycheck.yml` (manual private/dev key health). Pin Ubuntu 24.04, Gradle 8.14.5, JDK 17, AGP 8.5.2.

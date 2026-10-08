# Study Assistant Android APK

This is a separate native Android companion app for the local Study Assistant project. It does not modify or depend on the existing Jarvis Android app.

## Features in the mobile MVP

- Local-only study material editor
- Import `.txt` and `.md` files from the phone
- Deterministic on-device summary view
- Local flashcard creation and review
- True/false quiz generation from saved material
- Android system Text-to-Speech
- Share/export of saved study material
- No network permission and no cloud account required

The full Python pipeline remains available under `study-assistant/` for PDF/audio ingestion, local embeddings, hybrid retrieval, Ollama, and Streamlit. This APK is intentionally lightweight and uses Android platform APIs so it can be installed and used directly on a phone.

## Install

Download the `app-debug.apk` artifact from the GitHub Actions workflow named **Study assistant Android APK**, then open it on the phone and allow installation from the browser/files app when Android asks.

The debug APK is signed by the Android build system for testing. It is suitable for personal installation, not Play Store publishing.

## Build in CI

Every push affecting this directory runs:

```text
gradle -p study-assistant-android assembleDebug
```

The resulting artifact is uploaded as `study-assistant-debug-apk-<commit-sha>`.

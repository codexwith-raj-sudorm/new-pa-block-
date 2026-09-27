Project Memory & Progress Tracker (Memory.md)
Current Project State
 * Project Name: Jarvis Assistant Client (new-pa-block)
 * Package: com.jarvis.app
 * Target OS: Android API 29+
 * Primary Objective: Build a system-level, purely voice-operated digital assistant with a 3D holographic UI that runs entirely on free/local APIs without any text-based chat interface.
1. Active Phase
 * Current Phase: Phase 1 (Core System Hook & Default Assistant Setup)
 * Status: Ready to begin implementation.
 * Next Immediate Steps:
   * Create res/xml/assistant_service_config.xml.
   * Implement JarvisVoiceInteractionService to handle service binding.
   * Implement JarvisVoiceSessionService and an empty JarvisVoiceSession.
   * Update AndroidManifest.xml with permissions (BIND_VOICE_INTERACTION, RECORD_AUDIO) and intent-filters.
2. Completed Milestones
 * Documentation Setup: PRD, Architecture, Rules, Phases, and Design specifications are fully drafted and finalized.
 * UI/UX Direction Selected: 3D Holographic Neural Matrix (fibonacci sphere layout) with purely audio-reactive volume and state changes.
3. Technical Context & Constraints
 * Framework: Jetpack Compose (Material 3) with hardware-accelerated Canvas operations.
 * Architecture: MVVM + Kotlin Coroutines (StateFlow for state, Dispatchers.IO for network/audio).
 * System Component: Extends Android's VoiceInteractionSession to draw overlay windows without requiring SYSTEM_ALERT_WINDOW permissions.
 * Hardware Profile: Optimized for low overhead to prevent thermal throttling on entry-level devices (e.g., Vivo Y20i).
 * Inference Pipeline: Microphone buffer (RMS tracking) \rightarrow Android Native STT \rightarrow Free-tier LLM API (Arena) or Local Server \rightarrow Android Native TTS.
4. Current State Machine (VoiceSession)
| State | Active System Component | Visual Output |
|---|---|---|
| IDLE | Background VoiceInteractionService | None (Process sleeping) |
| LISTENING | AudioCaptureManager (STT active) | Neural Matrix pulses with mic input |
| THINKING | LlmDispatcher (Network request) | Accelerated core spin, bright amber |
| SPEAKING | TtsSynthesizer (TTS playback) | Expanding shockwaves matched to TTS |
| DISMISS | Teardown & hide() invoked | HUD collapses to scale 0.0 |
5. Important Notes for Next AI Interaction
 * Begin by scaffolding the AndroidManifest.xml and the assistant_service_config.xml files.
 * Remember that the AI cannot use any paid APIs (like standard OpenAI/Anthropic endpoints) and must strictly route logic to Arena AI or local Termux-hosted endpoints.
 * UI code must solely use the 3D Canvas approach mapped out in Design.md; do not introduce standard Android text fields or message lists.

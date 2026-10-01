Implementation Phases (Phases.md)
Phase 1: Core System Hook & Default Assistant Setup
 * Objective: Establish the Android VoiceInteractionService framework so the OS recognizes the application as a digital assistant.
 * Deliverables:
   * Define assistant_service_config.xml with session and recognition service declarations.
   * Implement JarvisVoiceInteractionService to handle service binding and lifecycle callbacks.
   * Implement JarvisVoiceSessionService and an empty JarvisVoiceSession.
   * Configure AndroidManifest.xml with required intent-filters (android.service.voice.VoiceInteractionService, android.intent.action.ASSIST).
   * Create an onboarding intent to navigate the user to Android's default assistant settings screen.
 * Success Criteria: Long-pressing the power button or swiping from the bottom screen corners opens a blank system-level overlay without crashing.
Phase 2: Visual Engine & 3D Neural Matrix HUD
 * Objective: Build the Jetpack Compose 3D animated holographic interface.
 * Deliverables:
   * Implement the 3D Fibonacci sphere algorithm and projection logic in JarvisNeuralMatrix.kt.
   * Add the Y-axis revolution and X/Z cinematic tilt transformations.
   * Integrate audio-reactive scaling to expand the node lattice based on incoming amplitude.
   * Implement the state machine transitions:
     * LISTENING: Steady idle rotation with reactive mic pulse.
     * THINKING: High-speed concentric rotation with tightened radius.
     * SPEAKING: Concentric pulse matched to audio playback.
     * DISMISS: Smooth alpha-scale fade-out.
   * Embed the Compose view into JarvisVoiceSession.onCreateContentView().
 * Success Criteria: Invoking the assistant displays the revolving golden neural sphere floating over the active application.
Phase 3: Screen Context Extraction (Vision & Assist Layer)
 * Objective: Scrape active screen text and application metadata when invoked.
 * Deliverables:
   * Configure SHOW_WITH_ASSIST and SHOW_WITH_SCREENSHOT flags on session launch.
   * Implement ScreenContentExtractor.kt to recursively traverse the AssistStructure view hierarchy.
   * Extract visible TextView, EditText, and contentDescription attributes while skipping hidden views.
   * Extract active web URLs and app deep links from AssistContent.
   * Add defensive fallbacks for windows protected by FLAG_SECURE.
 * Success Criteria: Invoking the assistant while viewing a web article or document accurately extracts readable screen text into structured logging.
Phase 4: Voice Pipeline (STT & TTS Integration)
 * Objective: Enable bidirectional voice communication without text inputs.
 * Deliverables:
   * Implement AudioCaptureManager.kt using native SpeechRecognizer (or on-device Vosk) to stream transcribed user speech.
   * Compute real-time RMS audio amplitude from the microphone buffer and feed it to the Compose HUD.
   * Implement TtsSynthesizer.kt using Android's native TextToSpeech engine.
   * Map TTS audio energy back into the neural matrix animation during speech output.
   * Wire the TTS completion listener to automatically call VoiceInteractionSession.hide() once speech finishes.
 * Success Criteria: Speaking to the assistant updates the HUD in real-time, transcribes voice to text, and dismisses the window cleanly after speaking the response.
Phase 5: Zero-Cost LLM Engine & Orchestration
 * Objective: Connect the transcription and screen context to free-tier inference endpoints.
 * Deliverables:
   * Build LlmDispatcher.kt using Retrofit/OkHttp to handle network requests asynchronously.
   * Implement ArenaApiClient.kt for zero-cost remote model inference.
   * Add support for local inference backends (e.g., local server endpoints running via Termux/Ollama).
   * Build a prompt assembler that merges the current screen context with the user's spoken query.
   * Enforce safety constraints: maximum 5 reasoning iterations to prevent infinite token loops, plus request timeouts with graceful TTS error fallbacks.
 * Success Criteria: A spoken question about the current screen content sends a prompt to the free LLM and speaks the answer aloud through the HUD.
Phase 6: Performance Optimization & Edge Cases
 * Objective: Harden the system for real-world daily use.
 * Deliverables:
   * Optimize Canvas rendering and math allocations to guarantee stable 60/120 FPS on entry-level hardware.
   * Verify clean memory teardown on session dismiss to prevent background battery drain.
   * Implement interruption handling: tapping the screen or speaking mid-response immediately cancels active network calls and resets the state.
   * Handle edge cases: network disconnection, mic permission revocation, and silent/do-not-disturb modes.
 * Success Criteria: Assistant triggers reliably in under 200 ms, operates smoothly across varied apps, and consumes negligible idle battery.

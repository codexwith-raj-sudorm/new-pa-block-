Project Requirements Document (PRD)
1. Project Overview
Name: Jarvis Assistant Client (new-pa-block)
Platform: Android (API 29+)
Description: A system-level, purely voice-operated Android digital assistant designed to replace Google Assistant or Gemini. The application features a 3D animated holographic neural matrix HUD that responds in real-time to voice interactions. It operates completely free of charge by strictly utilizing open-source models, local processing, and zero-cost APIs.
2. Target Audience
 * Android Power Users: Individuals looking to replace their default digital assistant with a highly customized, sci-fi-themed alternative.
 * Privacy & Open-Source Enthusiasts: Users who prefer local inference or strictly free/open-source APIs without subscription paywalls.
 * Developers: Users wanting a programmable system-level assistant capable of reading screen context and executing device commands.
3. Core Principles & Constraints
 * Strictly No Paid APIs: The system must not rely on any commercial API that requires a credit card or billing. It will utilize local on-device processing (e.g., local LLMs) or strictly free-tier LLM providers (e.g., Arena AI).
 * Screenless UI: No chat boxes, text inputs, or scrollable feeds. The interface is 100% voice-operated and visually represented exclusively by an animated 3D canvas overlay.
 * System Native: Must integrate directly into Android's VoiceInteractionService to be triggered natively via hardware buttons, corner swipes, and standard OS assist invocations.
4. Key Features
4.1 System-Level Assist Integration
 * Default Assistant Routing: Users can assign the app as the default Android digital assistant to intercept OS-level triggers.
 * Contextual Screen Awareness: Reads the active view hierarchy, foreground URLs, and on-screen text using Android's AssistState payload.
 * Seamless Overlay: Renders over any active application using a transparent VoiceInteractionSession window without pausing the underlying app.
4.2 Immersive Voice-Responsive UI
 * Jarvis Neural Matrix: A Jetpack Compose 3D spherical canvas that revolutes on a tilted Y-axis.
 * Audio-Reactive Physics: The 3D hologram scales, pulses, and shifts neural node brightness based on the root-mean-square (RMS) amplitude of the incoming mic data and the outgoing Text-to-Speech (TTS) audio buffer.
 * Auto-Lifecycle: The visual overlay automatically transitions through Idle, Listening, Thinking, and Speaking states, smoothly fading out once the assistant finishes speaking.
4.3 Voice Processing Pipeline
 * Wake Word Detection: Background hotword listening using AlwaysOnHotwordDetector for hands-free activation.
 * Speech-to-Text (STT): Captures user audio seamlessly upon launch and transcribes it using native on-device Android STT.
 * Text-to-Speech (TTS): Delivers the LLM's response purely through auditory output, mapped directly to the visual HUD pulses.
5. Non-Goals (Out of Scope)
 * Standard text-based messaging UI or conversation history logs.
 * Paid third-party API integrations (e.g., OpenAI API, Anthropic API).
 * A standalone launcher or home screen replacement.


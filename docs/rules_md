Rules Document (Rules.md)
1. Zero-Cost & Open-Source Requirement
 * Strict Prohibition of Paid Services: The application must never integrate commercial LLM providers that require billing or credit card registration (e.g., OpenAI, Anthropic, standard Google Cloud AI).
 * Allowed Remote APIs: Use strictly free-tier endpoints such as Arena AI. Ensure that the API client respects rate limits and handles prompt iterations efficiently (capping multi-step logic to a safe maximum of 5 iterations to avoid token looping).
 * Local Inference Integration: If utilizing on-device LLMs, bridge the Android application to a local server environment (e.g., running an Ollama or Flask backend via Termux). Models must be quantized and lightweight enough to execute efficiently on entry-level hardware like a Vivo Y20i without causing thermal throttling or aggressive OS memory kills.
2. VoiceInteractionService Best Practices
 * Keep the Background Service Lightweight: The JarvisVoiceInteractionService runs continuously. It must remain exceptionally lightweight to prevent Android from terminating it. Never execute heavy operations or UI rendering in this class.
 * Isolate UI in the Session Service: All heavy-weight operations, including inflating the Compose UI, managing the 3D Canvas, and processing visual logic, must be isolated within VoiceInteractionSessionService, which runs in a separate process.
 * Audio Hardware Arbitration: Always release the AudioRecord microphone lock immediately when transitioning from the "Listening" state to the "Thinking" state to avoid blocking the hardware from other apps.
3. UI/UX & Presentation Rules
 * No Screen-Based Interfaces: The system must not present traditional Android activities, chat lists, or text input fields.
 * 3D Canvas Rendering Only: The visual interface is strictly limited to the JarvisNeuralMatrix Compose 3D overlay.
 * Non-Blocking Overlay: The VoiceInteractionSession window must be transparent and allow the user to see the underlying application. It must automatically tear down and invoke hide() the moment the text-to-speech engine fires the completion callback.
4. State Management & Concurrency
 * Unidirectional Data Flow: Use Kotlin StateFlow to push states (Idle, Listening, Thinking, Speaking) from the ViewModel to the Compose UI. Do not allow the UI to mutate its own state.
 * Coroutines for Background Tasks: All network requests, context parsing, and heavy computation must run on Dispatchers.IO. All UI updates and canvas invalidations must execute on Dispatchers.Main.
 * State Interruption: If a user triggers the assistant while it is already processing a previous query, the system must immediately cancel the active Coroutine Job, flush the audio buffer, and reset to the "Listening" state.
5. Security & Privacy Constraints
 * On-Demand Context: Screen scraping via AssistStructure and AssistContent must only execute when explicitly triggered by the user. Do not continuously poll the screen.
 * Secure Flag Respect: The app must gracefully handle null or blocked view nodes when interacting with applications that utilize FLAG_SECURE (e.g., banking or password managers).
 * Local Logging Only: To maintain privacy, do not send crash logs, voice data, or on-screen text to remote telemetry servers. Keep all debugging logs local.

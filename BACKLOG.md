# Jarvis Backlog (parked ideas — not MVP)

Post-MVP candidates, roughly ordered by excitement. Promote one at a time.

## Interfaces
- [ ] **Telegram bot** — native phone UI, push notifications for reminders (likely first post-MVP)
- [ ] **Voice I/O** — wake-word (openWakeWord) → STT (faster-whisper) → TTS (Piper); API already text-based so this slots in
- [ ] **Android bridge** — Termux:API/Tasker companion for on-phone actions (battery, location, notifications)

## Brain & memory
- [ ] **Semantic recall** — sqlite-vec/Chroma over facts + conversation log (LIKE-match works for now)
- [ ] **Conversation summarization** — rolling summary for long chats to save tokens
- [ ] **Fallback model** — cheap model (haiku/gpt-mini/gemini-flash) when primary fails or for trivial messages
- [ ] **Ollama path** — verify LiteLLM → local model swap for the privacy story

## Tools
- [ ] **Calendar + email** — Google OAuth integrations (biggest auth lift; high value)
- [ ] **Smart home** — Home Assistant API bridge
- [ ] **File uploads** — chat with PDFs/images (Chainlit supports attachments natively)
- [ ] **Scheduled nudges** — proactive check-ins (needs always-on host + job runner)

## Ops
- [ ] **Persistent memory on host** — external DB (Turso/Supabase) since free-tier disks are ephemeral
- [ ] **Token usage dashboard** — per-day spend visible in UI (spend is tracked; just needs display)
- [ ] **Multi-user** — real accounts (Chainlit supports OAuth; data model needs user scoping)

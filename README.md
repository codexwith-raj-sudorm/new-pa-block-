# Jarvis MVP 🤖

A personal AI assistant (Jarvis-style) — chat-first, mobile-friendly web UI, cloud brain.

**Status:** Phase 0 scaffold — chat UI + echo stub + health endpoint.
See [PLAN.md](PLAN.md) for the full architecture and build phases.

## Run it

```bash
pip install -r requirements.txt
cp .env.example .env   # fill in keys in Phase 1
python server.py
```

Open http://localhost:8000 (or the live preview URL in this sandbox).

## Layout

- `server.py` — FastAPI server (API + web UI)
- `web/index.html` — mobile-first chat UI (single file, no build)
- `config.yaml` — assistant/llm/server settings
- `agent/`, `llm/`, `tools/`, `memory/` — arrive in Phases 1–3

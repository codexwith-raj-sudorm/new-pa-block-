# Jarvis MVP 🤖

A personal AI assistant (Jarvis-style) — chat-first, mobile-friendly, cloud brain.
Built on **[Chainlit](https://github.com/Chainlit/chainlit)** + **LiteLLM** (Gemini).

**Status:** Rev 3 — adopted Chainlit base. Demo mode in sandbox, real brain on deploy.
See [PLAN.md](PLAN.md) for architecture, phases, and customization roadmap.

## Run it (dev)

```bash
pip install -r requirements.txt
cp .env.example .env   # add GEMINI_API_KEY for the real brain
chainlit run app.py -w # -w = auto-reload; serves on http://localhost:8000
```

No key / no network? Jarvis runs in clearly-labeled **demo mode** so the
UI, history, and streaming stay testable.

## Deploy (Render, free tier)

This repo ships a `render.yaml` Blueprint:

1. Render dashboard → New → Blueprint → pick this repo, branch `arena/01a08a6b-new-pa-block`
2. Set `GEMINI_API_KEY` when prompted → Deploy
3. Open the `https://jarvis-mvp.onrender.com` URL → talk to real Jarvis

## Layout

- `app.py` — the Chainlit app (entrypoint)
- `chainlit.md` — welcome screen; `.chainlit/config.toml` — UI branding
- `server.py`, `web/`, `llm/`, `memory/`, `agent/` — pre-Chainlit scaffold, kept for reference
  (design + prompts reused; runtime is Chainlit now)
- `config.yaml` — assistant settings (being migrated into Chainlit config)

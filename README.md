# Jarvis 🤖

Personal AI assistant — Flask + Google Gemini, mobile-friendly dark chat UI.

**Adopted base:** [abinasharma001/flask-gemini-chatbot](https://github.com/abinasharma001/flask-gemini-chatbot)
(MIT) — Jarvis-ified with persona, conversation history, demo mode, and health checks.
`check_models.py` diagnostic also comes from the base. Thank you!

## Features

- 💬 Chat with 10-turn conversation memory (per browser session)
- 🔄 Free-tier model fallback chain (`gemini-2.5-flash-lite` → `2.5-flash` → `3-flash`)
- 📻 Demo mode when no API key — UI fully testable, clock works
- 🛡️ Per-IP rate limit (3s), token budgets, `/health` endpoint
- 📱 Responsive dark UI, phone-first

## Run it

```bash
pip install -r requirements.txt
cp .env.example .env   # add GEMINI_API_KEY for the real brain
python app.py          # http://localhost:8000
```

No key? Jarvis runs in clearly-labeled **demo mode**.

## Deploy (Koyeb, free)

1. Koyeb → Create Service → GitHub → this repo, branch `arena/01a08a6b-new-pa-block`
2. Auto-detects Python + `Procfile`. Instance **Free**
3. Env vars: `GEMINI_API_KEY`, `FLASK_SECRET` (any random string), `PORT` (auto)
4. Deploy → open your `*.koyeb.app` URL
5. UptimeRobot free ping every 5 min → never sleeps (no quota on Koyeb free)

Render works too (`Procfile` + `requirements.txt` are all it needs).

## Diagnose the key/models (on host with network)

```bash
python check_models.py   # lists models your key can use
```

## Customize

- Persona/prompt: `JARVIS_PROMPT` in `app.py`
- Models: `MODEL_PRIORITY` list · Rate limit: `COOLDOWN_SEC` · History: `MAX_TURNS`
- UI: `templates/index.html` (single file, no build step)

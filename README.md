# Jarvis MVP 🤖

A personal AI assistant (Jarvis-style) — chat-first, mobile-friendly, cloud brain.
Built on **[Chainlit](https://github.com/Chainlit/chainlit)** + **LiteLLM** (Gemini).

See [PLAN.md](PLAN.md) for architecture, phases, and roadmap.

## Features (13 tools)

| Area | Tools | Try |
|---|---|---|
| ⏰ General | `get_time`, `calculate` | "what time is it?", "calc 15% of 240" |
| 🔍 Web | `web_search`, `fetch_page` | "search for ...", "fetch https://..." |
| 🧠 Memory | `remember`, `recall` | "remember I like tea", "recall tea" |
| ✍️ Notes | `note_add`, `note_list` | "note buy milk", "my notes" |
| ✅ Todos | `todo_add`, `todo_list`, `todo_done` | "todo call mom", "todos", "todo done 1" |
| ⏰ Reminders | `reminder_add`, `reminders_due` | "remind me to stretch in 10 minutes" |

## Run it (dev)

```bash
pip install -r requirements.txt
cp .env.example .env   # add GEMINI_API_KEY for the real brain
chainlit run app.py -w # serves on http://localhost:8000
```

No key / no network? Jarvis runs in **demo mode** — the 11 offline tools
still run for real via a keyword router, so everything stays testable.

## Tests

```bash
python3 tests/test_tools.py   # tools + agent loop (21 checks)
python3 tests/test_memory.py  # memory + reminder parsing (30 checks)
```

CI (`.github/workflows/ci.yml`) runs both on every push.

## Deploy

**Koyeb (recommended, free):** connect this repo → Python/Docker → env
`LLM_PROVIDER=gemini`, `GEMINI_API_KEY`, `GEMINI_MODEL=gemini-2.0-flash`,
plus `JARVIS_USER`/`JARVIS_PASSWORD` to lock the UI with a login.
Keep awake with a free UptimeRobot ping every 5 min.

**Render (fallback):** `render.yaml` Blueprint in repo.

**Docker (any host):** `docker build -t jarvis . && docker run -p 8000:8000 --env-file .env jarvis`

Set `JARVIS_PASSWORD` anywhere the URL is public — the login wall activates automatically.

## Layout

- `app.py` — the Chainlit app (entrypoint: brain loop, demo router, auth)
- `agent/runner.py` — provider-agnostic agentic tool loop
- `tools/` — `general.py`, `web.py`, `memory_tools.py`, `registry.py`, `demo_router.py`
- `memory/store.py` — SQLite: facts, notes, todos, reminders, conversation log
- `tests/` — zero-dependency test scripts
- `chainlit.md`, `.chainlit/` — welcome screen + UI branding
- `server.py`, `web/`, `llm/`, `agent/prompts.py` — pre-Chainlit scaffold (reference only)

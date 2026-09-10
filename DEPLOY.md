# Deploy Jarvis (free)

All three hosts deploy from this repo's `arena/01a08a6b-new-pa-block` branch.
Every push auto-redeploys once connected.

## Env vars (all hosts)

| Key | Value | Notes |
|---|---|---|
| `LLM_PROVIDER` | `gemini` | `demo` = offline script (dev only) |
| `GEMINI_API_KEY` | your key | From aistudio.google.com |
| `GEMINI_MODEL` | `gemini-2.0-flash` | — |
| `JARVIS_USER` / `JARVIS_PASSWORD` | you choose | **Set these!** Login wall for your public URL |
| `DAILY_SPEND_CAP_USD` | `2.0` | Safety cap on API spend |
| `TAVILY_API_KEY` | (optional) | Better web search; DuckDuckGo otherwise |
| `TIMEZONE` | `Asia/Kolkata` | Your timezone |

## Option A — Koyeb (recommended)

1. koyeb.com → sign up with GitHub → **Create Service** → **GitHub**
2. Repo `codexwith-raj-sudorm/new-pa-block-`, branch `arena/01a08a6b-new-pa-block`
3. Builder auto-detects Python + `Procfile`. Instance **Free**, region Frankfurt/Washington
4. Add env vars above → **Deploy** → open your `*.koyeb.app` URL
5. Free tier sleeps after 1h idle (~5s wake). For 24/7: free **UptimeRobot**
   monitor on your URL every 5 min (no hourly quota on Koyeb — stays awake free)

## Option B — Render (fallback)

1. render.com → **New** → **Blueprint** → pick repo, branch `arena/...`
2. Set `GEMINI_API_KEY` (+ others) when prompted → Deploy (`render.yaml` in repo)
3. Free tier: sleeps after 15 min (~1 min wake), 750 hrs/mo quota.
   A pinger keeps it awake but consumes the whole quota for one service.

## Option C — Docker (any host / VPS)

```bash
docker build -t jarvis .
docker run -d -p 8000:8000 --env-file .env --restart unless-stopped jarvis
```

## Notes

- **Ephemeral disks:** free tiers wipe local files on sleep/redeploy, so
  memory (SQLite) resets. Fine for now; external DB (Turso/Supabase) is in BACKLOG.md.
- **First wake:** free tiers cold-start; first message after idle is slow, then fast.
- **Key safety:** keys live in host env vars / `.env` only — never in git.

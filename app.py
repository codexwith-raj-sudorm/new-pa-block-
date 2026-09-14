"""Jarvis — personal AI assistant (Flask + Google Gemini).

Adopted base: abinasharma001/flask-gemini-chatbot (MIT, see README credits).
Jarvis upgrades: persona, conversation history, no-key demo mode,
/health endpoint, friendlier rate limit, bigger token budget.
"""

import os
import time
import uuid
from datetime import datetime

from dotenv import load_dotenv
from flask import Flask, jsonify, render_template, request, session

load_dotenv()

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET") or os.urandom(24)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()

# Free-tier model fallback chain (tried in order on quota exhaustion).
MODEL_PRIORITY = ["gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-3-flash"]

MODELS = []
if GEMINI_API_KEY:
    import google.generativeai as genai
    from google.api_core.exceptions import ResourceExhausted

    genai.configure(api_key=GEMINI_API_KEY)
    MODELS = [(name, genai.GenerativeModel(name)) for name in MODEL_PRIORITY]

JARVIS_PROMPT = """You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone.
Be warm, a little witty, and genuinely helpful. Keep answers short enough to read on a phone screen unless asked for detail. Use short paragraphs and bullet points where they help.

{history}Question: {question}

Answer:"""

HISTORY = {}  # session_id -> [{"q":..., "a":...}] (in-memory, last 10 turns)
MAX_TURNS = 10
LAST_REQUEST_TIME = {}
COOLDOWN_SEC = 3


def _session_id():
    sid = session.get("sid")
    if not sid:
        sid = session["sid"] = uuid.uuid4().hex
    return sid


def _format_history(turns):
    if not turns:
        return ""
    lines = ["Conversation so far:"]
    for t in turns[-MAX_TURNS:]:
        lines.append(f"User: {t['q']}")
        lines.append(f"Jarvis: {t['a']}")
    return "\n".join(lines) + "\n\n"


def _token_limit(question):
    n = len(question)
    if n <= 20:
        return 256
    if n <= 80:
        return 512
    return 1024


def _allowed(ip):
    now = time.time()
    if now - LAST_REQUEST_TIME.get(ip, 0) < COOLDOWN_SEC:
        return False
    LAST_REQUEST_TIME[ip] = now
    if len(LAST_REQUEST_TIME) > 5000:
        LAST_REQUEST_TIME.clear()
    return True


def _demo_reply(question):
    q = question.lower()
    if "time" in q or "date" in q or "day" in q:
        now = datetime.now().strftime("%A, %d %B %Y, %I:%M %p")
        return (
            f"📻 Demo mode — but the clock works: it's {now}.\n\n"
            "Set GEMINI_API_KEY where I'm hosted and I'll answer everything for real."
        )
    short = question if len(question) <= 200 else question[:200] + "…"
    return (
        f'📻 Demo mode — you asked: "{short}"\n\n'
        "I'm Jarvis running without a brain key. "
        "Set GEMINI_API_KEY where I'm hosted and I'll answer for real."
    )


def _query(question, turns):
    prompt = JARVIS_PROMPT.format(question=question, history=_format_history(turns))
    budget = _token_limit(question)
    for name, model in MODELS:
        try:
            resp = model.generate_content(prompt, generation_config={"max_output_tokens": budget})
            return resp.text, name
        except ResourceExhausted:
            print(f"WARNING: quota exceeded → {name}, trying next model")
            continue
        except Exception as e:
            return f"⚠️ Error: {e}", "error"
    return "⚠️ All AI models are busy (free-tier quotas). Please try again in a minute.", "none"


@app.route("/health")
def health():
    return jsonify(
        {"status": "ok", "brain": "connected" if MODELS else "demo", "models": MODEL_PRIORITY}
    )


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/chatbot", methods=["POST"])
def chatbot():
    if not _allowed(request.remote_addr or "local"):
        return jsonify({"response": "⏳ Please wait a few seconds before sending another message."})
    data = request.get_json(force=True, silent=True) or {}
    question = (data.get("question") or "").strip()
    if not question:
        return jsonify({"response": "Please ask me something!"})
    sid = _session_id()
    turns = HISTORY.setdefault(sid, [])
    if not MODELS:
        answer, model = _demo_reply(question), "demo"
    else:
        answer, model = _query(question, turns)
    turns.append({"q": question, "a": answer})
    del turns[:-MAX_TURNS]
    if len(HISTORY) > 1000:
        HISTORY.clear()
    return jsonify({"response": answer, "model": model})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", "8000")))

"""System prompt builder: Jarvis persona + live facts + current time."""

import os
from datetime import datetime

from memory import store

try:
    from zoneinfo import ZoneInfo

    _TZ = ZoneInfo(os.getenv("TIMEZONE", "Asia/Kolkata"))
except Exception:
    _TZ = None

BASE_PROMPT = (
    "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. "
    "Be warm, a little witty, and genuinely helpful. Keep answers short enough to read "
    "comfortably on a phone screen unless asked for detail. "
    "Use short paragraphs and occasional bullets. "
    "You have tools: get_time, calculate, web_search, fetch_page, remember, recall, "
    "note_add, note_list, todo_add, todo_list, todo_done, reminder_add, reminders_due. "
    "Use them when relevant: time/date, math, current/external info, saving or looking up "
    "memories/notes/todos/reminders. When the user says 'remember ...', call remember. "
    "When you use web_search, cite sources briefly."
)


def _local_now():
    now = datetime.now(_TZ) if _TZ else datetime.utcnow()
    return now.strftime("%A, %d %B %Y, %I:%M %p")


def build_system_prompt(max_facts=10):
    """Persona + timestamp + auto-injected memories (pure-ish, tested)."""
    parts = [BASE_PROMPT, f"Current local time: {_local_now()}."]
    try:
        facts = store.search_facts("", limit=max_facts)
    except Exception:
        facts = []
    if facts:
        parts.append(
            "Things you remember about your owner:\n"
            + "\n".join(f"- {r['text']}" for r in facts)
        )
    return "\n".join(parts)

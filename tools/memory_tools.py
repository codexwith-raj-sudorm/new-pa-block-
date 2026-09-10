"""Memory tools: facts, notes, todos, reminders. SQLite-backed, fully offline."""

import re
from datetime import datetime, timedelta, timezone

from memory import store

try:
    from zoneinfo import ZoneInfo

    _IST = ZoneInfo("Asia/Kolkata")
except Exception:
    _IST = None


def _tool(name, description, properties, required=None):
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required or [],
            },
        },
    }


REMEMBER_SCHEMA = _tool(
    "remember",
    "Save a fact about the user for the long term (preferences, details, context).",
    {"text": {"type": "string", "description": "The fact to remember."}},
    ["text"],
)
RECALL_SCHEMA = _tool(
    "recall",
    "Look up saved facts. Empty query lists recent memories.",
    {"query": {"type": "string", "description": "Keyword to search memories."}},
)
NOTE_ADD_SCHEMA = _tool(
    "note_add", "Save a quick note.", {"text": {"type": "string"}}, ["text"]
)
NOTE_LIST_SCHEMA = _tool("note_list", "List recent notes.", {})
TODO_ADD_SCHEMA = _tool(
    "todo_add", "Add a todo item.", {"text": {"type": "string"}}, ["text"]
)
TODO_LIST_SCHEMA = _tool("todo_list", "List open todos.", {})
TODO_DONE_SCHEMA = _tool(
    "todo_done",
    "Mark a todo done by its #id.",
    {"todo_id": {"type": "integer", "description": "Todo #id from todo_list."}},
    ["todo_id"],
)
REMINDER_ADD_SCHEMA = _tool(
    "reminder_add",
    "Set a reminder. 'when' accepts 'in 10 minutes', 'in 2 hours', 'at 18:00', 'at 6:30 pm'.",
    {
        "text": {"type": "string", "description": "What to remind about."},
        "when": {"type": "string", "description": "When: 'in 10 minutes', 'at 18:00', ..."},
    },
    ["text", "when"],
)
REMINDERS_DUE_SCHEMA = _tool(
    "reminders_due", "Check which reminders are due right now.", {}
)


def _fmt_dt(iso):
    try:
        dt = datetime.fromisoformat(iso)
        if _IST:
            dt = dt.astimezone(_IST)
        return dt.strftime("%d %b, %I:%M %p")
    except Exception:
        return iso


def remember(text):
    text = (text or "").strip()
    if not text:
        return "Error: nothing to remember — give me the fact."
    fid = store.add_fact(text)
    return f"Noted! I'll remember that. (memory #{fid})"


def recall(query=""):
    rows = store.search_facts(query or "")
    if not rows:
        return "I don't have any saved memories matching that yet."
    return "Here's what I remember:\n" + "\n".join(f"#{r['id']}: {r['text']}" for r in rows)


def note_add(text):
    text = (text or "").strip()
    if not text:
        return "Error: empty note."
    nid = store.add_note(text)
    return f"Note #{nid} saved. ✍️"


def note_list():
    rows = store.list_notes()
    if not rows:
        return "No notes yet. Say `note ...` to save one."
    return "Your notes:\n" + "\n".join(f"#{r['id']}: {r['text']} ({_fmt_dt(r['created_at'])})" for r in rows)


def todo_add(text):
    text = (text or "").strip()
    if not text:
        return "Error: empty todo."
    tid = store.add_todo(text)
    return f"Todo #{tid} added: {text}"


def todo_list():
    rows = store.list_todos()
    if not rows:
        return "Todo list is clear. Nothing to do! ✅"
    return "Open todos:\n" + "\n".join(f"#{r['id']}: {r['text']}" for r in rows)


def todo_done(todo_id):
    if store.complete_todo(todo_id):
        return f"Todo #{todo_id} marked done. Nice work! ✅"
    return f"Couldn't find open todo #{todo_id}. Say `todos` to see the list."


def parse_when(s):
    """Mini natural-date parser -> UTC ISO string, or None."""
    s = (s or "").strip().lower()
    now_utc = datetime.now(timezone.utc)
    m = re.fullmatch(r"in (\d+)\s*(minutes?|mins?|hours?|hrs?|days?)", s)
    if m:
        n, unit = int(m.group(1)), m.group(2)
        if unit.startswith("min"):
            delta = timedelta(minutes=n)
        elif unit.startswith("h"):
            delta = timedelta(hours=n)
        else:
            delta = timedelta(days=n)
        return (now_utc + delta).isoformat()
    m = re.fullmatch(r"at (\d{1,2})(?::(\d{2}))?\s*(am|pm)?", s)
    if m:
        hr, mn, ap = int(m.group(1)), int(m.group(2) or 0), m.group(3)
        if ap == "pm" and hr < 12:
            hr += 12
        if ap == "am" and hr == 12:
            hr = 0
        if hr > 23 or mn > 59:
            return None
        base = datetime.now(_IST) if _IST else now_utc
        target = base.replace(hour=hr, minute=mn, second=0, microsecond=0)
        if target <= base:
            target += timedelta(days=1)
        return target.astimezone(timezone.utc).isoformat()
    try:
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=_IST or timezone.utc)
        return dt.astimezone(timezone.utc).isoformat()
    except Exception:
        return None


def reminder_add(text, when):
    text = (text or "").strip()
    if not text:
        return "Error: what should I remind you about?"
    iso = parse_when(when)
    if not iso:
        return "I couldn't understand when. Try 'in 10 minutes', 'in 2 hours', 'at 18:00', or 'at 6:30 pm'."
    rid = store.add_reminder(text, iso)
    return f"⏰ Reminder #{rid} set: '{text}' — {_fmt_dt(iso)}. I'll announce it when you open chat."


def reminders_due():
    rows = store.due_reminders()
    if not rows:
        return "Nothing due right now. ✅"
    lines = ["⏰ Due reminders:"]
    for r in rows:
        lines.append(f"#{r['id']}: {r['text']} (due {_fmt_dt(r['remind_at'])})")
        store.mark_reminder_done(r["id"])
    return "\n".join(lines)

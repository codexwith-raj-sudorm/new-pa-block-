"""SQLite memory: facts, notes, todos, reminders + conversation log.

DB lives at data/jarvis.db (JARVIS_DB env overrides — tests use a temp file).
Dependency-free, and every function degrades gracefully.
"""

import os
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

DB_PATH = Path(os.getenv("JARVIS_DB", Path(__file__).resolve().parent.parent / "data" / "jarvis.db"))

SCHEMA = """
CREATE TABLE IF NOT EXISTS facts(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS todos(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, done INTEGER DEFAULT 0, due_at TEXT, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, remind_at TEXT NOT NULL, done INTEGER DEFAULT 0, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS conversations(id TEXT PRIMARY KEY, created_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id);
"""

_inited = set()


def _connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    if str(DB_PATH) not in _inited:
        conn.executescript(SCHEMA)
        conn.commit()
        _inited.add(str(DB_PATH))
    return conn


def _now():
    return datetime.now(timezone.utc).isoformat()


def _rows(cursor):
    return [dict(r) for r in cursor.fetchall()]


# ---- facts ("remember ...") ----


def add_fact(text):
    conn = _connect()
    cur = conn.execute("INSERT INTO facts(text, created_at) VALUES (?, ?)", (text.strip(), _now()))
    conn.commit()
    fid = cur.lastrowid
    conn.close()
    return fid


def search_facts(query="", limit=5):
    conn = _connect()
    if (query or "").strip():
        cur = conn.execute(
            "SELECT * FROM facts WHERE text LIKE ? ORDER BY id DESC LIMIT ?",
            (f"%{query.strip()}%", limit),
        )
    else:
        cur = conn.execute("SELECT * FROM facts ORDER BY id DESC LIMIT ?", (limit,))
    out = _rows(cur)
    conn.close()
    return out


# ---- notes ----


def add_note(text):
    conn = _connect()
    cur = conn.execute("INSERT INTO notes(text, created_at) VALUES (?, ?)", (text.strip(), _now()))
    conn.commit()
    nid = cur.lastrowid
    conn.close()
    return nid


def list_notes(limit=10):
    conn = _connect()
    cur = conn.execute("SELECT * FROM notes ORDER BY id DESC LIMIT ?", (limit,))
    out = _rows(cur)
    conn.close()
    return out


# ---- todos ----


def add_todo(text, due_at=None):
    conn = _connect()
    cur = conn.execute(
        "INSERT INTO todos(text, due_at, created_at) VALUES (?, ?, ?)", (text.strip(), due_at, _now())
    )
    conn.commit()
    tid = cur.lastrowid
    conn.close()
    return tid


def list_todos(include_done=False, limit=20):
    conn = _connect()
    if include_done:
        cur = conn.execute("SELECT * FROM todos ORDER BY done, id LIMIT ?", (limit,))
    else:
        cur = conn.execute("SELECT * FROM todos WHERE done = 0 ORDER BY id LIMIT ?", (limit,))
    out = _rows(cur)
    conn.close()
    return out


def complete_todo(todo_id):
    try:
        tid = int(todo_id)
    except (TypeError, ValueError):
        return False
    conn = _connect()
    cur = conn.execute("UPDATE todos SET done = 1 WHERE id = ? AND done = 0", (tid,))
    conn.commit()
    changed = cur.rowcount > 0
    conn.close()
    return changed


# ---- reminders ----


def add_reminder(text, remind_at):
    conn = _connect()
    cur = conn.execute(
        "INSERT INTO reminders(text, remind_at, created_at) VALUES (?, ?, ?)",
        (text.strip(), remind_at, _now()),
    )
    conn.commit()
    rid = cur.lastrowid
    conn.close()
    return rid


def due_reminders(now_iso=None):
    conn = _connect()
    cur = conn.execute(
        "SELECT * FROM reminders WHERE done = 0 AND remind_at <= ? ORDER BY remind_at",
        (now_iso or _now(),),
    )
    out = _rows(cur)
    conn.close()
    return out


def mark_reminder_done(reminder_id):
    conn = _connect()
    conn.execute("UPDATE reminders SET done = 1 WHERE id = ?", (reminder_id,))
    conn.commit()
    conn.close()


# ---- conversation log ----


def new_conversation():
    cid = uuid.uuid4().hex[:12]
    conn = _connect()
    conn.execute("INSERT INTO conversations(id, created_at) VALUES (?, ?)", (cid, _now()))
    conn.commit()
    conn.close()
    return cid


def log_message(conv_id, role, content):
    try:
        conn = _connect()
        conn.execute(
            "INSERT INTO messages(conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
            (conv_id, role, (content or "")[:4000], _now()),
        )
        conn.commit()
        conn.close()
    except Exception:
        pass  # logging must never break chat

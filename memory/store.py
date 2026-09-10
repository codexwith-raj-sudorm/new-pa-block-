"""SQLite memory store — conversations, messages (+ Phase 3 tables ready)."""

import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent / "data" / "jarvis.db"

SCHEMA = """
CREATE TABLE IF NOT EXISTS conversations(id TEXT PRIMARY KEY, title TEXT, created_at TEXT);
CREATE TABLE IF NOT EXISTS messages(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  conversation_id TEXT, role TEXT, content TEXT, created_at TEXT
);
CREATE TABLE IF NOT EXISTS user_profile(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE IF NOT EXISTS facts(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, created_at TEXT);
CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, created_at TEXT);
CREATE TABLE IF NOT EXISTS todos(
  id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, done INTEGER DEFAULT 0,
  due_at TEXT, created_at TEXT
);
CREATE TABLE IF NOT EXISTS reminders(
  id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, remind_at TEXT,
  done INTEGER DEFAULT 0, created_at TEXT
);
CREATE TABLE IF NOT EXISTS audit_log(
  id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT,
  tool TEXT, args_json TEXT, result_summary TEXT
);
CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id);
"""


def _connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def _now():
    return datetime.now(timezone.utc).isoformat()


def init_db():
    conn = _connect()
    conn.executescript(SCHEMA)
    conn.commit()
    conn.close()


def new_conversation_id():
    return uuid.uuid4().hex[:12]


def ensure_conversation(conv_id):
    """Return a valid conversation id, creating the row if needed."""
    if not conv_id:
        conv_id = new_conversation_id()
    conn = _connect()
    row = conn.execute("SELECT id FROM conversations WHERE id = ?", (conv_id,)).fetchone()
    if row is None:
        conn.execute(
            "INSERT INTO conversations(id, title, created_at) VALUES (?, ?, ?)",
            (conv_id, "Chat", _now()),
        )
        conn.commit()
    conn.close()
    return conv_id


def save_message(conv_id, role, content):
    conn = _connect()
    conn.execute(
        "INSERT INTO messages(conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
        (conv_id, role, content, _now()),
    )
    conn.commit()
    conn.close()


def get_history(conv_id, limit=30):
    """Last N messages, oldest-first, as [{role, content}]."""
    conn = _connect()
    rows = conn.execute(
        "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?",
        (conv_id, limit),
    ).fetchall()
    conn.close()
    return [{"role": r["role"], "content": r["content"]} for r in reversed(rows)]

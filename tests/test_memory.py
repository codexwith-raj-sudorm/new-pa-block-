"""Memory tests (isolated temp DB). Run: python3 tests/test_memory.py"""

import os
import sys
import tempfile
from datetime import datetime, timezone

tmp = tempfile.mkdtemp()
os.environ["JARVIS_DB"] = os.path.join(tmp, "test.db")
sys.path.insert(0, ".")

from memory import store
from tools import memory_tools as mt
from tools.demo_router import detect_demo_tool
from tools.registry import execute_tool

passed = failed = 0


def check(name, cond, info=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ✅ {name}")
    else:
        failed += 1
        print(f"  ❌ {name} {info}")


print("facts:")
check("remember", "memory #1" in execute_tool("remember", {"text": "I like filter coffee"}))
execute_tool("remember", {"text": "My dog is named Bruno"})
check("recall keyword", "Bruno" in execute_tool("recall", {"query": "dog"}))
check("recall all", "filter coffee" in execute_tool("recall", {}))
check("recall miss", "don't have" in execute_tool("recall", {"query": "zebra"}))

print("notes:")
check("note add", "Note #1" in execute_tool("note_add", {"text": "Buy milk"}))
check("note list", "Buy milk" in execute_tool("note_list", {}))

print("todos:")
check("todo add", "Todo #1" in execute_tool("todo_add", {"text": "Call mom"}))
check("todo list", "Call mom" in execute_tool("todo_list", {}))
check("todo done", "marked done" in execute_tool("todo_done", {"todo_id": 1}))
check("todo list empty", "clear" in execute_tool("todo_list", {}))
check("todo done missing", "Couldn't find" in execute_tool("todo_done", {"todo_id": 99}))

print("reminders:")
iso = mt.parse_when("in 10 minutes")
delta = (datetime.fromisoformat(iso) - datetime.now(timezone.utc)).total_seconds()
check("parse in-minutes", 540 < delta < 660, iso)
iso2 = mt.parse_when("in 2 hours")
delta2 = (datetime.fromisoformat(iso2) - datetime.now(timezone.utc)).total_seconds()
check("parse in-hours", 7000 < delta2 < 7400, iso2)
check("parse at-time", mt.parse_when("at 18:00") is not None)
check("parse garbage", mt.parse_when("someday maybe") is None)
check("reminder add", "Reminder #1" in execute_tool("reminder_add", {"text": "Stretch", "when": "in 1 minute"}))
check("reminder bad when", "couldn't understand" in execute_tool("reminder_add", {"text": "x", "when": "never o'clock"}))
store.add_reminder("Past item", "2000-01-01T00:00:00+00:00")
check("due fires", "Past item" in execute_tool("reminders_due", {}))
check("due marks done", "Past item" not in execute_tool("reminders_due", {}))

print("conversation log:")
cid = store.new_conversation()
store.log_message(cid, "user", "hello")
store.log_message(cid, "assistant", "hi there")
import sqlite3

conn = sqlite3.connect(os.environ["JARVIS_DB"])
n = conn.execute("SELECT COUNT(*) FROM messages WHERE conversation_id = ?", (cid,)).fetchone()[0]
conn.close()
check("messages logged", n == 2, f"n={n}")

print("demo router (memory):")
check("remember", (detect_demo_tool("remember I like coffee") or [None])[0] == "remember")
check("recall", (detect_demo_tool("recall coffee") or [None])[0] == "recall")
check("note add", (detect_demo_tool("note buy milk") or [None])[0] == "note_add")
check("note list", detect_demo_tool("my notes") == ("note_list", {}))
check("todo add", (detect_demo_tool("todo call mom") or [None])[0] == "todo_add")
check("todo list", detect_demo_tool("todos") == ("todo_list", {}))
check("todo done", detect_demo_tool("todo done 2") == ("todo_done", {"todo_id": 2}))
check(
    "reminder",
    (detect_demo_tool("remind me to stretch in 10 minutes") or [None])[0] == "reminder_add",
)
check("reminders list", detect_demo_tool("reminders") == ("reminders_due", {}))

print("registry:")
check("13 tools", len(__import__("tools.registry", fromlist=["TOOL_SCHEMAS"]).TOOL_SCHEMAS) == 13)

print("tool audit + prompt builder:")
execute_tool("calculate", {"expression": "1+1"})
calls = store.recent_tool_calls()
check("audit logged", len(calls) >= 1 and calls[0]["tool"] == "calculate", str(calls[:1]))
from agent.prompts import build_system_prompt

prompt = build_system_prompt()
check("prompt has persona", "Jarvis" in prompt)
check("prompt injects facts", "filter coffee" in prompt)
check("prompt has time", "2026" in prompt)

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)

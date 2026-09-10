"""Tool registry: schemas for the LLM + safe execution + audit log (13 tools)."""

import json

from memory import store
from tools.general import CALCULATE_SCHEMA, GET_TIME_SCHEMA, calculate, get_time
from tools.memory_tools import (
    NOTE_ADD_SCHEMA,
    NOTE_LIST_SCHEMA,
    RECALL_SCHEMA,
    REMEMBER_SCHEMA,
    REMINDER_ADD_SCHEMA,
    REMINDERS_DUE_SCHEMA,
    TODO_ADD_SCHEMA,
    TODO_DONE_SCHEMA,
    TODO_LIST_SCHEMA,
    note_add,
    note_list,
    recall,
    remember,
    reminder_add,
    reminders_due,
    todo_add,
    todo_done,
    todo_list,
)
from tools.web import FETCH_PAGE_SCHEMA, WEB_SEARCH_SCHEMA, fetch_page, web_search

TOOL_SCHEMAS = [
    GET_TIME_SCHEMA,
    CALCULATE_SCHEMA,
    WEB_SEARCH_SCHEMA,
    FETCH_PAGE_SCHEMA,
    REMEMBER_SCHEMA,
    RECALL_SCHEMA,
    NOTE_ADD_SCHEMA,
    NOTE_LIST_SCHEMA,
    TODO_ADD_SCHEMA,
    TODO_LIST_SCHEMA,
    TODO_DONE_SCHEMA,
    REMINDER_ADD_SCHEMA,
    REMINDERS_DUE_SCHEMA,
]

FUNCTIONS = {
    "get_time": get_time,
    "calculate": calculate,
    "web_search": web_search,
    "fetch_page": fetch_page,
    "remember": remember,
    "recall": recall,
    "note_add": note_add,
    "note_list": note_list,
    "todo_add": todo_add,
    "todo_list": todo_list,
    "todo_done": todo_done,
    "reminder_add": reminder_add,
    "reminders_due": reminders_due,
}


def execute_tool(name: str, arguments) -> str:
    """Run a tool by name. arguments may be a dict or JSON string. Never raises."""
    fn = FUNCTIONS.get(name)
    if fn is None:
        return f"Error: unknown tool '{name}'. Available: {', '.join(FUNCTIONS)}."
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments or "{}")
        except Exception:
            return f"Error: invalid arguments JSON for {name}."
    try:
        clean = {k: v for k, v in (arguments or {}).items() if v is not None}
        result = str(fn(**clean))
        store.log_tool(name, clean, result)
        return result
    except TypeError as e:
        return f"Error calling {name}: bad arguments ({e})."
    except Exception as e:  # never let a tool crash the loop
        return f"Error in {name}: {e}."

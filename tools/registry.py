"""Tool registry: schemas for the LLM + safe execution."""

import json

from tools.general import CALCULATE_SCHEMA, GET_TIME_SCHEMA, calculate, get_time
from tools.web import FETCH_PAGE_SCHEMA, WEB_SEARCH_SCHEMA, fetch_page, web_search

TOOL_SCHEMAS = [GET_TIME_SCHEMA, CALCULATE_SCHEMA, WEB_SEARCH_SCHEMA, FETCH_PAGE_SCHEMA]

FUNCTIONS = {
    "get_time": get_time,
    "calculate": calculate,
    "web_search": web_search,
    "fetch_page": fetch_page,
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
        return str(fn(**clean))
    except TypeError as e:
        return f"Error calling {name}: bad arguments ({e})."
    except Exception as e:  # never let a tool crash the loop
        return f"Error in {name}: {e}."

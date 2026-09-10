"""Agentic tool loop (provider-agnostic) + history trimming.

call_llm(messages) -> {"content": str|None, "tool_calls": [{id, name, arguments}]}
on_tool(name, args, result) -> awaitable UI hook (Chainlit Step in prod).
Returns the final assistant text.
"""

import json

from tools.registry import execute_tool

MAX_HISTORY = 30  # last N non-system messages sent to the LLM (token guard)


def trim_history(messages, keep=MAX_HISTORY):
    """Keep system message(s) + last `keep` others. Pure function, tested."""
    system = [m for m in messages if m.get("role") == "system"]
    rest = [m for m in messages if m.get("role") != "system"]
    return system + rest[-keep:]


async def run_with_tools(messages, call_llm, on_tool=None, max_iterations=5):
    working = trim_history(list(messages))
    for _ in range(max_iterations):
        resp = call_llm(working)
        content = resp.get("content") or ""
        calls = resp.get("tool_calls") or []
        if not calls:
            return content
        working.append(
            {
                "role": "assistant",
                "content": content,
                "tool_calls": [
                    {
                        "id": c["id"],
                        "type": "function",
                        "function": {
                            "name": c["name"],
                            "arguments": c.get("arguments")
                            if isinstance(c.get("arguments"), str)
                            else json.dumps(c.get("arguments", {})),
                        },
                    }
                    for c in calls
                ],
            }
        )
        for c in calls:
            result = execute_tool(c["name"], c.get("arguments", "{}"))
            if on_tool:
                await on_tool(c["name"], c.get("arguments"), result)
            working.append({"role": "tool", "tool_call_id": c["id"], "content": result})
    return "(stopped: too many tool steps — try a simpler request)"

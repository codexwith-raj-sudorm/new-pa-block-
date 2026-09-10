"""Keyword router so tools are testable in sandbox demo mode (no LLM needed).

On deploy the real brain picks tools itself; this only fires when
LLM_PROVIDER=demo. Returns (tool_name, args) or None.
"""

import re

_MATHY = re.compile(r"^[\d\s+\-*/().%^!]+$")


def detect_demo_tool(text: str):
    t = (text or "").strip()
    low = t.lower()

    # time / date
    if re.search(r"\b(time|date|day is it|clock)\b", low):
        return ("get_time", {})

    # calculator
    m = re.search(r"calc(?:ulate)?\s+(.+)", t, re.I)
    if m:
        return ("calculate", {"expression": m.group(1).strip().rstrip("?")})
    m = re.search(r"what is (.+?)\??$", low)
    if m and _MATHY.match(m.group(1)) and re.search(r"\d", m.group(1)):
        return ("calculate", {"expression": m.group(1).strip()})

    # memory: facts
    m = re.search(r"\bremember (?:that )?(.+)", t, re.I)
    if m:
        return ("remember", {"text": m.group(1).strip().rstrip("?")})
    m = re.search(r"\brecall\b\s*(.*)", t, re.I)
    if m and ("recall" in low or "what do you remember" in low or "my memor" in low):
        q = m.group(1).strip() if "recall" in low else ""
        return ("recall", {"query": q} if q else {})

    # memory: notes
    m = re.search(r"(?:take a )?note[ :]+(.+)", t, re.I)
    if m:
        return ("note_add", {"text": m.group(1).strip()})
    if re.search(r"\b(my notes|list notes|show notes|^notes$)\b", low):
        return ("note_list", {})

    # memory: todos (check "done N" before generic add)
    m = re.search(r"(?:todo )?done (\d+)", low)
    if m:
        return ("todo_done", {"todo_id": int(m.group(1))})
    m = re.search(r"(?:add )?todo[ :]+(.+)", t, re.I)
    if m:
        return ("todo_add", {"text": m.group(1).strip()})
    if re.search(r"\btodos?\b", low) and "done" not in low:
        return ("todo_list", {})

    # memory: reminders
    m = re.search(r"remind me to (.+?) (in .+|at .+)$", t, re.I)
    if m:
        return ("reminder_add", {"text": m.group(1).strip(), "when": m.group(2).strip()})
    if re.search(r"\breminders?\b", low):
        return ("reminders_due", {})

    # web
    m = re.search(r"search(?: the web)?(?: for)?\s+(.+)", t, re.I)
    if m:
        return ("web_search", {"query": m.group(1).strip().rstrip("?")})
    m = re.search(r"(open|fetch|read)\s+(https?://\S+)", t, re.I)
    if m:
        return ("fetch_page", {"url": m.group(2)})

    return None

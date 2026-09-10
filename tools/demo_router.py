"""Keyword router so tools are testable in sandbox demo mode (no LLM needed).

On deploy the real brain picks tools itself; this only fires when
LLM_PROVIDER=demo. Returns (tool_name, args) or None.
"""

import re

_MATHY = re.compile(r"^[\d\s+\-*/().%^!]+$")


def detect_demo_tool(text: str):
    t = (text or "").strip()
    low = t.lower()

    if re.search(r"\b(time|date|day is it|clock)\b", low):
        return ("get_time", {})

    m = re.search(r"calc(?:ulate)?\s+(.+)", t, re.I)
    if m:
        return ("calculate", {"expression": m.group(1).strip().rstrip("?")})

    m = re.search(r"what is (.+?)\??$", low)
    if m and _MATHY.match(m.group(1)) and re.search(r"\d", m.group(1)):
        return ("calculate", {"expression": m.group(1).strip()})

    m = re.search(r"search(?: the web)?(?: for)?\s+(.+)", t, re.I)
    if m:
        return ("web_search", {"query": m.group(1).strip().rstrip("?")})

    m = re.search(r"(open|fetch|read)\s+(https?://\S+)", t, re.I)
    if m:
        return ("fetch_page", {"url": m.group(2)})

    return None

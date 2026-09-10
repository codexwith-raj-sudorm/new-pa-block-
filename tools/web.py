"""Web tools: search + page fetch. Keyless (DuckDuckGo) so deploy just works.

Needs internet access to search engines — unavailable in the dev sandbox
(firewall), where these return a graceful 'unavailable' message instead.
Upgrade path: swap web_search internals for Tavily/Brave with an API key.
"""

import html as _html
import re
from urllib.parse import unquote, urlparse, parse_qs

import httpx

HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; JarvisBot/1.0)"}
TIMEOUT = 15.0

WEB_SEARCH_SCHEMA = {
    "type": "function",
    "function": {
        "name": "web_search",
        "description": "Search the web for current information. Returns snippets with sources.",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search query."},
                "max_results": {"type": "integer", "description": "How many results (1-8). Default: 5."},
            },
            "required": ["query"],
        },
    },
}

FETCH_PAGE_SCHEMA = {
    "type": "function",
    "function": {
        "name": "fetch_page",
        "description": "Fetch a web page and return its readable text (for grounding answers).",
        "parameters": {
            "type": "object",
            "properties": {
                "url": {"type": "string", "description": "Full http(s) URL to fetch."},
            },
            "required": ["url"],
        },
    },
}

_BLOCKED_HOSTS = ("localhost", "127.", "0.0.0.0", "169.254.", "10.", "192.168.", "[::")


def _clean(s: str) -> str:
    return _html.unescape(re.sub(r"<[^>]+>", "", s)).strip()


def _real_url(u: str) -> str:
    u = _html.unescape(u)
    if "uddg=" in u:  # DuckDuckGo redirect wrapper
        try:
            return unquote(parse_qs(urlparse(u).query).get("uddg", [u])[0])
        except Exception:
            return u
    if u.startswith("//"):
        return "https:" + u
    return u


def web_search(query: str, max_results: int = 5) -> str:
    query = (query or "").strip()
    if not query:
        return "Error: empty search query."
    max_results = max(1, min(int(max_results or 5), 8))
    try:
        # 1) Instant-answer JSON (often has a summary + related topics)
        r = httpx.get(
            "https://api.duckduckgo.com/",
            params={"q": query, "format": "json", "no_html": 1},
            headers=HEADERS,
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        data = r.json()
        lines = []
        if data.get("AbstractText"):
            lines.append(f"Summary: {data['AbstractText']} (source: {data.get('AbstractURL', '')})")
        for t in (data.get("RelatedTopics") or [])[:max_results]:
            if isinstance(t, dict) and t.get("Text"):
                lines.append(f"- {t['Text']} ({t.get('FirstURL', '')})")
        if lines:
            return "\n".join(lines[: max_results + 1])
        # 2) HTML fallback (titles + links)
        r = httpx.get(
            "https://html.duckduckgo.com/html/",
            params={"q": query},
            headers=HEADERS,
            timeout=TIMEOUT,
        )
        r.raise_for_status()
        hits = re.findall(r'class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>', r.text, re.S)
        out = []
        for href, title in hits[:max_results]:
            out.append(f"- {_clean(title)} ({_real_url(href)})")
        return "\n".join(out) if out else "No results found."
    except Exception as e:
        return f"Web search is unavailable right now ({e})."


def fetch_page(url: str, max_chars: int = 3000) -> str:
    u = (url or "").strip()
    if not u.startswith(("http://", "https://")):
        return "Error: URL must start with http:// or https://"
    if any(b in u for b in _BLOCKED_HOSTS):
        return "Error: that host is blocked."
    try:
        r = httpx.get(u, headers=HEADERS, timeout=TIMEOUT, follow_redirects=True)
        r.raise_for_status()
        raw = r.text
    except Exception as e:
        return f"Couldn't fetch that page ({e})."
    text = re.sub(r"<script.*?</script>|<style.*?</style>", " ", raw, flags=re.S | re.I)
    text = _html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", text))).strip()
    if len(text) > max_chars:
        text = text[:max_chars] + "…"
    return text or "Page had no readable text."

"""LLM router — one interface, many providers.

Phase 1: Gemini (free tier) via REST. Anthropic/OpenAI/Ollama plug in later as
new provider classes exposing the same chat()/stream() interface.
"""

import json
import os

import httpx

from llm.demo import DemoProvider

GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"


class LLMError(Exception):
    """Any LLM failure. Message is safe to show the user."""


def _to_gemini_contents(messages):
    """Convert [{role, content}] (roles: system/user/assistant) to Gemini format."""
    system = None
    contents = []
    for m in messages:
        role, text = m.get("role"), m.get("content", "")
        if role == "system":
            system = (system + "\n" if system else "") + text
        else:
            g_role = "model" if role == "assistant" else "user"
            if contents and contents[-1]["role"] == g_role:
                contents[-1]["parts"][0]["text"] += "\n" + text
            else:
                contents.append({"role": g_role, "parts": [{"text": text}]})
    if not contents or contents[0]["role"] != "user":
        contents.insert(0, {"role": "user", "parts": [{"text": "Hello"}]})
    return system, contents


class GeminiProvider:
    name = "gemini"

    def __init__(self, api_key=None, model=None, timeout=60.0):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY", "")
        self.model = model or os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
        self.timeout = timeout
        if not self.api_key:
            raise LLMError("missing-key")

    def _url(self, method):
        return f"{GEMINI_API_BASE}/models/{self.model}:{method}"

    def _payload(self, messages, max_tokens):
        system, contents = _to_gemini_contents(messages)
        body = {"contents": contents, "generationConfig": {"maxOutputTokens": max_tokens}}
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}
        return body

    def chat(self, messages, max_tokens=1024):
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.post(
                    self._url("generateContent"),
                    params={"key": self.api_key},
                    json=self._payload(messages, max_tokens),
                )
        except httpx.RequestError as e:
            raise LLMError(f"Network error reaching Gemini: {e}")
        if resp.status_code != 200:
            raise LLMError(f"Gemini API error ({resp.status_code}): {self._err_detail(resp)}")
        try:
            return resp.json()["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError):
            raise LLMError("Gemini returned an unexpected response (possibly blocked). Try rephrasing.")

    def stream(self, messages, max_tokens=1024):
        """Yield text chunks as they arrive (SSE from Gemini)."""
        try:
            with httpx.Client(timeout=self.timeout) as client:
                with client.stream(
                    "POST",
                    self._url("streamGenerateContent"),
                    params={"key": self.api_key, "alt": "sse"},
                    json=self._payload(messages, max_tokens),
                ) as resp:
                    if resp.status_code != 200:
                        body = resp.read()
                        raise LLMError(f"Gemini API error ({resp.status_code}): {self._err_detail_raw(body)}")
                    for line in resp.iter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if not data:
                            continue
                        try:
                            chunk = json.loads(data)
                            yield chunk["candidates"][0]["content"]["parts"][0]["text"]
                        except (KeyError, IndexError, ValueError):
                            continue
        except httpx.RequestError as e:
            raise LLMError(f"Network error reaching Gemini: {e}")

    @staticmethod
    def _err_detail(resp):
        try:
            return resp.json().get("error", {}).get("message", resp.text[:200])
        except Exception:
            return resp.text[:200]

    @staticmethod
    def _err_detail_raw(body: bytes):
        try:
            return json.loads(body).get("error", {}).get("message", body[:200].decode("utf-8", "ignore"))
        except Exception:
            return body[:200].decode("utf-8", "ignore")


def get_provider():
    name = os.getenv("LLM_PROVIDER", "gemini").lower()
    if name == "gemini":
        return GeminiProvider()
    raise LLMError(f"LLM provider '{name}' isn't wired yet. Set LLM_PROVIDER=gemini (Phase 1).")


def is_configured():
    try:
        get_provider()
        return True
    except LLMError:
        return False


def chat(messages, max_tokens=1024):
    return get_provider().chat(messages, max_tokens=max_tokens)


def stream(messages, max_tokens=1024):
    yield from get_provider().stream(messages, max_tokens=max_tokens)

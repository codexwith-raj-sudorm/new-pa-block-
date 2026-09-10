"""Jarvis MVP — assistant server (Phase 1: real brain + memory)."""

import json
import os
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel

load_dotenv()  # read .env (keys, provider, port) — never commit .env

from agent.prompts import SETUP_GUIDE, build_system_prompt
from llm.router import LLMError, chat as llm_chat, is_configured
from llm.router import stream as llm_stream
from memory.store import ensure_conversation, get_history, init_db, save_message

APP_VERSION = "0.2.0"
HISTORY_LIMIT = 30
BASE_DIR = Path(__file__).resolve().parent
WEB_DIR = BASE_DIR / "web"

init_db()

app = FastAPI(title="Jarvis MVP", version=APP_VERSION)


class ChatRequest(BaseModel):
    message: str
    conversation_id: Optional[str] = None


def _provider_name():
    return os.getenv("LLM_PROVIDER", "gemini").lower()


def _build_messages(conv_id, user_text):
    messages = [{"role": "system", "content": build_system_prompt()}]
    messages.extend(get_history(conv_id, limit=HISTORY_LIMIT))
    messages.append({"role": "user", "content": user_text})
    return messages


@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": APP_VERSION,
        "phase": 1,
        "provider": _provider_name(),
        "brain": "connected" if is_configured() else "missing-key",
    }


@app.post("/api/chat")
def chat(req: ChatRequest):
    """Non-streaming chat (fallback + simple clients)."""
    text = (req.message or "").strip()
    if not text:
        return {"reply": "Say something and I'll answer.", "conversation_id": req.conversation_id}
    conv_id = ensure_conversation(req.conversation_id)
    save_message(conv_id, "user", text)
    if not is_configured():
        return {"reply": SETUP_GUIDE, "conversation_id": conv_id}
    try:
        reply = llm_chat(_build_messages(conv_id, text))
    except LLMError as e:
        return {"reply": f"Brain glitch: {e}", "conversation_id": conv_id}
    save_message(conv_id, "assistant", reply)
    return {"reply": reply, "conversation_id": conv_id}


@app.get("/api/chat/stream")
def chat_stream(message: str, conversation_id: Optional[str] = None):
    """Streaming chat over SSE. Events: chunk | done | error (JSON in data:)."""
    text = (message or "").strip()
    conv_id = ensure_conversation(conversation_id)

    def gen():
        if not text:
            yield _sse({"type": "error", "message": "Empty message."})
            return
        save_message(conv_id, "user", text)
        if not is_configured():
            yield _sse({"type": "error", "message": SETUP_GUIDE})
            return
        full = []
        try:
            for chunk in llm_stream(_build_messages(conv_id, text)):
                full.append(chunk)
                yield _sse({"type": "chunk", "text": chunk})
        except LLMError as e:
            yield _sse({"type": "error", "message": f"Brain glitch: {e}"})
            return
        save_message(conv_id, "assistant", "".join(full))
        yield _sse({"type": "done", "conversation_id": conv_id})

    return StreamingResponse(gen(), media_type="text/event-stream")


def _sse(payload: dict):
    return f"data: {json.dumps(payload)}\n\n"


@app.get("/")
def index():
    return FileResponse(WEB_DIR / "index.html")


if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "8000"))
    uvicorn.run(app, host="0.0.0.0", port=port)

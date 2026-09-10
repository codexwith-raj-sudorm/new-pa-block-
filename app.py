"""Jarvis — Chainlit app with LiteLLM brain.

- On deploy (Render etc.) with GEMINI_API_KEY set: real Gemini brain, streaming.
- In this sandbox (LLM APIs firewall-blocked): clearly-labeled demo replies,
  so UI / history / streaming stay fully testable.
"""

import os

import chainlit as cl
import litellm
from dotenv import load_dotenv

load_dotenv()

APP_NAME = "Jarvis"
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
TEMPERATURE = 0.7
MAX_TOKENS = 1024

SYSTEM_PROMPT = (
    "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. "
    "Be warm, a little witty, and genuinely helpful. Keep answers short enough to read "
    "comfortably on a phone screen unless asked for detail. "
    "Use short paragraphs and occasional bullets."
)

DEMO_TEMPLATE = (
    "📻 *Demo mode* — this sandbox blocks AI APIs, so I'm on a script.\n\n"
    'You said: "{user}"\n\n'
    "Once deployed with the Gemini key, I answer for real. "
    "(Session history + streaming both work right now.)"
)


def _brain_available() -> bool:
    # LLM_PROVIDER=demo forces demo replies (sandbox dev). On deploy it's
    # set to gemini via render.yaml, so the real brain takes over.
    if os.getenv("LLM_PROVIDER", "gemini").lower() == "demo":
        return False
    return bool(os.getenv("GEMINI_API_KEY", "").strip())


@cl.on_chat_start
async def on_chat_start():
    cl.user_session.set("history", [{"role": "system", "content": SYSTEM_PROMPT}])
    if _brain_available():
        status = "Brain: connected 🟢"
    else:
        status = "Brain: demo mode 📻 (set GEMINI_API_KEY on deploy for the real brain)"
    await cl.Message(content=f"Hello. I am {APP_NAME}.\n{status}\n\nHow can I help?").send()


if hasattr(cl, "set_starters"):

    @cl.set_starters
    async def set_starters():
        return [
            cl.Starter(label="What can you do?", message="What can you do?"),
            cl.Starter(label="Surprise me", message="Tell me something interesting in two sentences."),
            cl.Starter(label="Who are you?", message="Introduce yourself."),
        ]


@cl.on_message
async def on_message(message: cl.Message):
    history = cl.user_session.get("history")
    history.append({"role": "user", "content": message.content})

    reply = cl.Message(content="")
    await reply.send()

    if not _brain_available():
        user_text = message.content if len(message.content) <= 200 else message.content[:200] + "…"
        text = DEMO_TEMPLATE.format(user=user_text)
        for word in text.split(" "):
            await reply.stream_token(word + " ")
        await reply.update()
        history.append({"role": "assistant", "content": text})
        return

    try:
        stream = litellm.completion(
            model=f"gemini/{GEMINI_MODEL}",
            messages=history,
            temperature=TEMPERATURE,
            max_tokens=MAX_TOKENS,
            api_key=os.getenv("GEMINI_API_KEY"),
            stream=True,
        )
        full = []
        for chunk in stream:
            delta = chunk.choices[0].delta.content or ""
            if delta:
                full.append(delta)
                await reply.stream_token(delta)
        await reply.update()
        history.append({"role": "assistant", "content": "".join(full)})
    except Exception as e:
        await reply.stream_token(f"\n\n⚠️ Brain glitch: {e}")
        await reply.update()

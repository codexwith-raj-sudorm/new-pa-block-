"""Jarvis — Chainlit app with LiteLLM brain + tools.

- On deploy (Render etc.) with GEMINI_API_KEY set: real Gemini brain with
  agentic tool loop (time, calculator, web search, page fetch).
- In this sandbox (LLM APIs firewall-blocked): demo mode. A keyword router
  still runs the offline tools for real, so they're testable in the preview.
"""

import json
import os

import chainlit as cl
import litellm
from dotenv import load_dotenv

from agent.runner import run_with_tools
from tools.demo_router import detect_demo_tool
from tools.registry import TOOL_SCHEMAS, execute_tool

load_dotenv()

APP_NAME = "Jarvis"
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
TEMPERATURE = 0.7
MAX_TOKENS = 1024

SYSTEM_PROMPT = (
    "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. "
    "Be warm, a little witty, and genuinely helpful. Keep answers short enough to read "
    "comfortably on a phone screen unless asked for detail. "
    "Use short paragraphs and occasional bullets. "
    "You have tools: get_time, calculate, web_search, fetch_page. "
    "Use them when the user asks about time/date, math, or current/external information. "
    "When you use web_search, cite sources briefly."
)

DEMO_TEMPLATE = (
    "📻 *Demo mode* — this sandbox blocks AI APIs, so I'm on a script.\n\n"
    'You said: "{user}"\n\n'
    "Try the tools: `what time is it?` · `calc 15% of 240` · `search for ...` · "
    "`fetch https://...` — those run for real even here."
)


def _brain_available() -> bool:
    # LLM_PROVIDER=demo forces demo replies (sandbox dev). On deploy it's
    # set to gemini via render.yaml, so the real brain takes over.
    if os.getenv("LLM_PROVIDER", "gemini").lower() == "demo":
        return False
    return bool(os.getenv("GEMINI_API_KEY", "").strip())


async def _stream_text(reply: cl.Message, text: str):
    for word in text.split(" "):
        await reply.stream_token(word + " ")
    await reply.update()


@cl.on_chat_start
async def on_chat_start():
    cl.user_session.set("history", [{"role": "system", "content": SYSTEM_PROMPT}])
    if _brain_available():
        status = "Brain: connected 🟢 · Tools: time, calculator, web search, page fetch 🛠️"
    else:
        status = (
            "Brain: demo mode 📻 (set GEMINI_API_KEY on deploy for the real brain)\n"
            "Tools: time + calculator run for real here · web tools need deploy"
        )
    await cl.Message(content=f"Hello. I am {APP_NAME}.\n{status}\n\nHow can I help?").send()


if hasattr(cl, "set_starters"):

    @cl.set_starters
    async def set_starters():
        return [
            cl.Starter(label="⏰ What time is it?", message="what time is it?"),
            cl.Starter(label="🔢 Calculate", message="calc 15% of 240"),
            cl.Starter(label="🔍 Web search", message="search for today's top tech news"),
        ]


def _litellm_call(messages):
    """Synchronous LiteLLM call normalized for the runner (single-user MVP)."""
    resp = litellm.completion(
        model=f"gemini/{GEMINI_MODEL}",
        messages=messages,
        tools=TOOL_SCHEMAS,
        temperature=TEMPERATURE,
        max_tokens=MAX_TOKENS,
        api_key=os.getenv("GEMINI_API_KEY"),
    )
    msg = resp.choices[0].message
    calls = [
        {"id": tc.id, "name": tc.function.name, "arguments": tc.function.arguments}
        for tc in (msg.tool_calls or [])
    ]
    return {"content": msg.content or "", "tool_calls": calls}


@cl.on_message
async def on_message(message: cl.Message):
    history = cl.user_session.get("history")
    history.append({"role": "user", "content": message.content})

    reply = cl.Message(content="")
    await reply.send()

    if not _brain_available():
        await _handle_demo(message.content, history, reply)
        return

    async def on_tool(name, args, result):
        async with cl.Step(name=f"🔧 {name}", type="tool") as step:
            step.input = args if isinstance(args, str) else json.dumps(args)
            step.output = (result or "")[:2000]

    try:
        text = await run_with_tools(history, _litellm_call, on_tool)
    except Exception as e:
        text = f"⚠️ Brain glitch: {e}"
    await _stream_text(reply, text or "(empty reply)")
    history.append({"role": "assistant", "content": text})


async def _handle_demo(user_text, history, reply):
    found = detect_demo_tool(user_text)
    if not found:
        short = user_text if len(user_text) <= 200 else user_text[:200] + "…"
        text = DEMO_TEMPLATE.format(user=short)
        await _stream_text(reply, text)
        history.append({"role": "assistant", "content": text})
        return
    name, args = found
    async with cl.Step(name=f"🔧 {name}", type="tool") as step:
        result = execute_tool(name, args)
        step.output = (result or "")[:2000]
    text = f"{result}\n\n_(via demo tool `{name}` — on deploy the AI picks tools itself.)_"
    await _stream_text(reply, text)
    history.append({"role": "assistant", "content": text})

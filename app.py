"""Jarvis — Chainlit app with LiteLLM brain + tools + memory.

- On deploy (Render/Koyeb) with GEMINI_API_KEY set: real Gemini brain with
  agentic tool loop (time, calculator, web, memory, notes, todos, reminders).
- In this sandbox (LLM APIs firewall-blocked): demo mode. A keyword router
  still runs the offline tools for real, so they're testable in the preview.
- Set JARVIS_PASSWORD (+ optional JARVIS_USER) to lock the UI with a login.
- Safety rails: 20 msg/min rate limit + daily LLM spend cap (default $2).
"""

import json
import os

import chainlit as cl
import litellm
from dotenv import load_dotenv

from agent.limits import RateLimiter, budget_ok
from agent.runner import run_with_tools
from memory import store
from tools.demo_router import detect_demo_tool
from tools.registry import TOOL_SCHEMAS, execute_tool

load_dotenv()

APP_NAME = "Jarvis"
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.0-flash")
TEMPERATURE = 0.7
MAX_TOKENS = 1024
DAILY_CAP_USD = os.getenv("DAILY_SPEND_CAP_USD", "2.0")
RATE_LIMITER = RateLimiter(max_calls=20, window_sec=60)

SYSTEM_PROMPT = (
    "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. "
    "Be warm, a little witty, and genuinely helpful. Keep answers short enough to read "
    "comfortably on a phone screen unless asked for detail. "
    "Use short paragraphs and occasional bullets. "
    "You have tools: get_time, calculate, web_search, fetch_page, remember, recall, "
    "note_add, note_list, todo_add, todo_list, todo_done, reminder_add, reminders_due. "
    "Use them when relevant: time/date, math, current/external info, saving or looking up "
    "memories/notes/todos/reminders. When the user says 'remember ...', call remember. "
    "When you use web_search, cite sources briefly."
)

DEMO_TEMPLATE = (
    "📻 *Demo mode* — this sandbox blocks AI APIs, so I'm on a script.\n\n"
    'You said: "{user}"\n\n'
    "Try the offline tools: `what time is it?` · `calc 15% of 240` · `remember I like tea` · "
    "`recall` · `note buy milk` · `my notes` · `todo call mom` · `todos` · "
    "`remind me to stretch in 10 minutes` · `search for ...` · `fetch https://...`"
)

# Login wall (only active when JARVIS_PASSWORD is set — e.g. on public hosting).
if os.getenv("JARVIS_PASSWORD"):

    @cl.password_auth_callback
    def auth_callback(username: str, password: str):
        if username == os.getenv("JARVIS_USER", "owner") and password == os.getenv("JARVIS_PASSWORD"):
            return cl.User(identifier=username, metadata={"role": "owner"})
        return None


def _brain_available() -> bool:
    # LLM_PROVIDER=demo forces demo replies (sandbox dev). On deploy it's
    # set to gemini, so the real brain takes over.
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
    conv_id = store.new_conversation()
    cl.user_session.set("conv_id", conv_id)
    if _brain_available():
        status = (
            f"Brain: connected 🟢 · Tools: 13 🛠️ · Spend cap ${DAILY_CAP_USD}/day "
            f"(used ${store.day_spend():.4f})"
        )
    else:
        status = (
            "Brain: demo mode 📻 (set GEMINI_API_KEY on deploy for the real brain)\n"
            "Tools: all 11 offline tools run for real here · web tools need deploy"
        )
    greeting = f"Hello. I am {APP_NAME}.\n{status}\n\nHow can I help?"
    due = store.due_reminders()
    if due:
        greeting += "\n\n⏰ While you were away:\n" + "\n".join(
            f"#{r['id']}: {r['text']}" for r in due
        )
        for r in due:
            store.mark_reminder_done(r["id"])
    await cl.Message(content=greeting).send()


if hasattr(cl, "set_starters"):

    @cl.set_starters
    async def set_starters():
        return [
            cl.Starter(label="⏰ What time is it?", message="what time is it?"),
            cl.Starter(label="🧠 Remember this", message="remember I take my coffee at 7am"),
            cl.Starter(label="🔍 Web search", message="search for today's top tech news"),
        ]


def _litellm_call(messages, _sink=None):
    """LiteLLM call normalized for the runner. Appends USD cost to _sink if given."""
    resp = litellm.completion(
        model=f"gemini/{GEMINI_MODEL}",
        messages=messages,
        tools=TOOL_SCHEMAS,
        temperature=TEMPERATURE,
        max_tokens=MAX_TOKENS,
        api_key=os.getenv("GEMINI_API_KEY"),
    )
    if _sink is not None:
        try:
            _sink.append(float(litellm.completion_cost(completion_response=resp) or 0))
        except Exception:
            pass
    msg = resp.choices[0].message
    calls = [
        {"id": tc.id, "name": tc.function.name, "arguments": tc.function.arguments}
        for tc in (msg.tool_calls or [])
    ]
    return {"content": msg.content or "", "tool_calls": calls}


@cl.on_message
async def on_message(message: cl.Message):
    if not RATE_LIMITER.allow():
        await cl.Message(content="⏳ Slow down a little — 20 messages per minute max.").send()
        return
    history = cl.user_session.get("history")
    conv_id = cl.user_session.get("conv_id")
    history.append({"role": "user", "content": message.content})
    store.log_message(conv_id, "user", message.content)

    reply = cl.Message(content="")
    await reply.send()

    if not _brain_available():
        await _handle_demo(message.content, history, conv_id, reply)
        return

    if not budget_ok(DAILY_CAP_USD):
        text = (
            f"💰 Daily spend cap (${DAILY_CAP_USD}) reached — I'm resting the brain until tomorrow "
            f"(used ${store.day_spend():.4f} today). Offline tools below still work: time, calc, "
            "memory, notes, todos, reminders."
        )
        await _stream_text(reply, text)
        history.append({"role": "assistant", "content": text})
        store.log_message(conv_id, "assistant", text)
        return

    async def on_tool(name, args, result):
        async with cl.Step(name=f"🔧 {name}", type="tool") as step:
            step.input = args if isinstance(args, str) else json.dumps(args)
            step.output = (result or "")[:2000]

    try:
        sink = []
        text = await run_with_tools(history, lambda m: _litellm_call(m, sink), on_tool)
        store.log_spend(sum(sink))
    except Exception as e:
        text = f"⚠️ Brain glitch: {e}"
    await _stream_text(reply, text or "(empty reply)")
    history.append({"role": "assistant", "content": text})
    store.log_message(conv_id, "assistant", text)


async def _handle_demo(user_text, history, conv_id, reply):
    found = detect_demo_tool(user_text)
    if not found:
        short = user_text if len(user_text) <= 200 else user_text[:200] + "…"
        text = DEMO_TEMPLATE.format(user=short)
        await _stream_text(reply, text)
        history.append({"role": "assistant", "content": text})
        store.log_message(conv_id, "assistant", text)
        return
    name, args = found
    async with cl.Step(name=f"🔧 {name}", type="tool") as step:
        result = execute_tool(name, args)
        step.output = (result or "")[:2000]
    text = f"{result}\n\n_(via demo tool `{name}` — on deploy the AI picks tools itself.)_"
    await _stream_text(reply, text)
    history.append({"role": "assistant", "content": text})
    store.log_message(conv_id, "assistant", text)

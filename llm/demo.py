"""Demo provider — offline stand-in brain for sandboxed dev.

The sandbox firewall blocks all LLM APIs, so this scripted Jarvis keeps the
UI / memory / streaming loop fully testable here. On deploy (Render etc.),
set LLM_PROVIDER=gemini and the real brain takes over. Every reply is
clearly labeled so demo mode is never mistaken for the real thing.
"""

import random

_OPENERS = [
    "Demo circuits engaged.",
    "Running on backup script — the sandbox blocks my AI APIs.",
    "This is Demo-Jarvis. Short version: I can't think yet, but I can chat.",
]


class DemoProvider:
    name = "demo"

    def __init__(self, *args, **kwargs):
        pass

    def chat(self, messages, max_tokens=1024):
        user_text = ""
        for m in reversed(messages):
            if m.get("role") == "user":
                user_text = m.get("content", "")
                break
        if len(user_text) > 200:
            user_text = user_text[:200] + "…"
        opener = random.choice(_OPENERS)
        return (
            f"📻 *Demo mode* — {opener}\n\n"
            f'You said: "{user_text}"\n\n'
            "I heard you, I remembered you (history works!), and I streamed this "
            "word by word. Once we're deployed with the Gemini key, I'll answer for real."
        )

    def stream(self, messages, max_tokens=1024):
        # Yield word-by-word so the streaming UI gets a real workout.
        for word in self.chat(messages, max_tokens).split(" "):
            yield word + " "

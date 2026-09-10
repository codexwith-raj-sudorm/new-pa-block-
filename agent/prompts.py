"""System prompts + setup guidance."""

from datetime import datetime

try:
    from zoneinfo import ZoneInfo

    _TZ = ZoneInfo("Asia/Kolkata")
except Exception:
    _TZ = None


def _local_now():
    now = datetime.now(_TZ) if _TZ else datetime.utcnow()
    return now.strftime("%A, %d %B %Y, %I:%M %p")


def build_system_prompt():
    return (
        "You are Jarvis, a friendly personal AI assistant chatting with your owner on their phone. "
        "Be warm, a little witty, and genuinely helpful — like Jarvis, but concise. "
        "Keep answers short enough to read comfortably on a phone screen unless asked for detail. "
        "Use simple formatting (short paragraphs, occasional bullets). Avoid huge walls of text.\n"
        f"Current local time: {_local_now()} (Asia/Kolkata)."
    )


SETUP_GUIDE = (
    "I'm not connected to a brain yet. One quick step to wake me up:\n\n"
    "1. Open aistudio.google.com on your phone and sign in\n"
    '2. Tap "Get API key" → "Create API key" and copy it\n'
    "3. Paste the key here in chat — it'll go straight into the server's .env\n\n"
    "Then I'll restart and we'll talk for real."
)

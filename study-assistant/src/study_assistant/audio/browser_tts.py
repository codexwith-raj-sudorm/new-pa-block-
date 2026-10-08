"""Zero-cost browser SpeechSynthesis output for the Streamlit UI."""

from __future__ import annotations

import html
import json


def speech_synthesis_html(text: str, *, button_label: str = "Read aloud") -> str:
    safe_text = json.dumps(text)
    safe_label = html.escape(button_label)
    return f"""
    <button onclick='speechSynthesis.cancel(); speechSynthesis.speak(new SpeechSynthesisUtterance({safe_text}));'
      style='padding:0.45rem 0.8rem;border-radius:0.4rem;border:1px solid #777;background:#222;color:#fff;'>
      {safe_label}
    </button>
    """

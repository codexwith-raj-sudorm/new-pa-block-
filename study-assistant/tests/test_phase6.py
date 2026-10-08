from __future__ import annotations

from pathlib import Path

from study_assistant.audio import speech_synthesis_html
from study_assistant.environment import collect_environment_metadata


def test_browser_tts_is_local_html_and_escapes_label() -> None:
    html = speech_synthesis_html("Read this locally", button_label="Read & play")
    assert "speechSynthesis" in html
    assert "Read &amp; play" in html
    assert "Read this locally" in html


def test_environment_metadata_is_compact(tmp_path: Path) -> None:
    metadata = collect_environment_metadata(tmp_path / "var")
    assert metadata["python_version"]
    assert metadata["staging_root"]

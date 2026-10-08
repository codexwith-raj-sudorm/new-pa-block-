"""Runtime metadata and setup diagnostics for reproducible local jobs."""

from __future__ import annotations

import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any


def _tool_version(command: str) -> str | None:
    executable = shutil.which(command)
    if not executable:
        return None
    try:
        completed = subprocess.run(
            [executable, "-version"],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    first_line = (completed.stdout or completed.stderr).splitlines()
    return first_line[0].strip() if first_line else executable


def collect_environment_metadata(staging_root: str | os.PathLike[str]) -> dict[str, Any]:
    """Collect compact, non-secret metadata alongside each staged source."""

    root = Path(staging_root).expanduser()
    try:
        root.mkdir(parents=True, exist_ok=True)
        free_bytes = shutil.disk_usage(root).free
    except OSError:
        free_bytes = None
    return {
        "python_version": platform.python_version(),
        "python_implementation": platform.python_implementation(),
        "python_executable": sys.executable,
        "platform": platform.platform(),
        "machine": platform.machine(),
        "ffmpeg_version": _tool_version("ffmpeg"),
        "ffprobe_version": _tool_version("ffprobe"),
        "staging_root": str(root.resolve()),
        "free_disk_bytes_at_ingest": free_bytes,
    }

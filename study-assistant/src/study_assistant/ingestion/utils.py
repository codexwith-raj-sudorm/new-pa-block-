"""Filesystem and source identity helpers."""

from __future__ import annotations

import hashlib
import os
import re
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from ..errors import IngestionError


def resolved_file(path: str | os.PathLike[str]) -> Path:
    candidate = Path(path).expanduser()
    if not candidate.exists():
        raise IngestionError(f"source file does not exist: {candidate}")
    if not candidate.is_file():
        raise IngestionError(f"source path is not a file: {candidate}")
    return candidate.resolve()


def sha256_file(path: Path, block_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(block_size), b""):
                digest.update(block)
    except OSError as exc:
        raise IngestionError(f"could not read source file {path}: {exc}") from exc
    return digest.hexdigest()


def canonical_url(value: str) -> str:
    """Normalize a URL enough for stable IDs, without dropping video IDs."""

    parsed = urlsplit(value.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise IngestionError("YouTube source must be an absolute http(s) URL")
    query = urlencode(sorted(parse_qsl(parsed.query, keep_blank_values=True)))
    return urlunsplit((parsed.scheme.lower(), parsed.netloc.lower(), parsed.path, query, ""))


def safe_name(value: str, fallback: str = "source") -> str:
    result = re.sub(r"[^A-Za-z0-9._-]+", "_", value).strip("._")
    return result[:100] or fallback

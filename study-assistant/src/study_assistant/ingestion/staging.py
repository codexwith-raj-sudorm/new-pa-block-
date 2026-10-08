"""Atomic, inspectable local staging for Phase 1 records."""

from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path
from typing import Iterable

from ..contracts import IngestionDocument, TextChunk
from ..environment import collect_environment_metadata
from ..errors import StagingError


def _atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", delete=False
        ) as stream:
            temporary = stream.name
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except OSError as exc:
        if temporary:
            try:
                os.unlink(temporary)
            except OSError:
                pass
        raise StagingError(f"could not stage {path.name}: {exc}") from exc


def stage_records(
    root: str | os.PathLike[str],
    document: IngestionDocument,
    chunks: Iterable[TextChunk],
    environment: dict[str, object] | None = None,
) -> Path:
    """Write a document and chunks atomically under ``root/<source_id>``."""

    stage_dir = Path(root).expanduser() / document.source.source_id
    if environment is None:
        environment = collect_environment_metadata(root)
    chunk_list = tuple(chunks)
    _atomic_text(
        stage_dir / "ingest.json",
        json.dumps(document.as_dict(), ensure_ascii=False, indent=2, sort_keys=True) + "\n",
    )
    lines = "".join(
        json.dumps(chunk.as_dict(), ensure_ascii=False, sort_keys=True) + "\n"
        for chunk in chunk_list
    )
    _atomic_text(stage_dir / "chunks.jsonl", lines)
    if environment is not None:
        _atomic_text(
            stage_dir / "environment.json",
            json.dumps(environment, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        )
    return stage_dir

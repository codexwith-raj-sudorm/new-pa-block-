from __future__ import annotations

import json
from pathlib import Path

import pytest

from study_assistant.contracts import (
    IngestionDocument,
    SourceRef,
    TranscriptSegment,
)
from study_assistant.errors import ValidationError
from study_assistant.ingestion.chunking import ChunkingConfig, DeterministicChunker
from study_assistant.ingestion.media import validate_audio_file
from study_assistant.ingestion.normalize import normalize_text
from study_assistant.ingestion.staging import stage_records


def make_document(text: str) -> IngestionDocument:
    source = SourceRef(
        source_id="source-test",
        source_type="audio",
        original_uri="lecture.wav",
        display_name="Lecture",
        checksum="abc123",
    )
    segments = tuple(
        TranscriptSegment(text=part, start_seconds=index * 10, end_seconds=(index + 1) * 10)
        for index, part in enumerate(text.split("\n\n"))
    )
    return IngestionDocument(
        source=source,
        title="Lecture",
        text=text,
        language="en",
        duration_seconds=20,
        metadata={"fixture": "true"},
        segments=segments,
    )


def test_normalize_text_preserves_paragraphs_and_unicode() -> None:
    value = "  Café\r\n\r\n\r\nnew\u0000\t line  "
    assert normalize_text(value) == "Café\n\nnew line"


def test_chunking_is_deterministic_and_keeps_time_provenance() -> None:
    document = make_document("Alpha beta gamma.\n\nDelta epsilon zeta.")
    chunker = DeterministicChunker(ChunkingConfig(chunk_size_chars=20, chunk_overlap_chars=4))
    first = chunker.chunk(document)
    second = chunker.chunk(document)

    assert first == second
    assert [chunk.ordinal for chunk in first] == list(range(len(first)))
    assert first[0].source_id == document.source.source_id
    assert first[0].start_seconds == 0
    assert first[-1].end_seconds == 20
    assert all(chunk.metadata["chunk_config"] == "recursive-v1:20:4" for chunk in first)


def test_stage_records_are_json_and_jsonl(tmp_path: Path) -> None:
    document = make_document("A short fixture.")
    chunks = DeterministicChunker().chunk(document)
    stage_dir = stage_records(tmp_path, document, chunks)

    stored_document = json.loads((stage_dir / "ingest.json").read_text())
    stored_chunks = [
        json.loads(line)
        for line in (stage_dir / "chunks.jsonl").read_text().splitlines()
    ]
    assert stored_document["source"]["source_id"] == "source-test"
    assert len(stored_chunks) == len(chunks)
    environment = json.loads((stage_dir / "environment.json").read_text())
    assert environment["python_version"]
    assert not list(tmp_path.rglob(".*.ingest.json.*"))


def test_contracts_reject_invalid_coordinates() -> None:
    with pytest.raises(ValidationError):
        TranscriptSegment(text="bad", start_seconds=2, end_seconds=1)
    with pytest.raises(ValidationError):
        ChunkingConfig(chunk_size_chars=10, chunk_overlap_chars=10)


def test_audio_validation_rejects_unknown_extension(tmp_path: Path) -> None:
    path = tmp_path / "notes.txt"
    path.write_text("not audio")
    with pytest.raises(Exception, match="unsupported audio extension"):
        validate_audio_file(path)

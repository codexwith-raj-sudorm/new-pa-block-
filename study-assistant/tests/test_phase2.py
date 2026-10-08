from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from study_assistant.contracts import TextChunk
from study_assistant.embeddings import HashEmbeddingProvider, SemanticIndex, load_staged_chunks
from study_assistant.embeddings.contracts import IndexProgress, VectorRecord
from study_assistant.errors import EmbeddingError, IndexStateError, ModelMismatchError, ValidationError


def make_chunks(source_id: str = "source-a") -> tuple[TextChunk, ...]:
    return (
        TextChunk(
            chunk_id=f"{source_id}-0", source_id=source_id,
            text="Algebra uses variables and equations.", ordinal=0,
            page_start=2, page_end=2, metadata={"topic": "math"},
        ),
        TextChunk(
            chunk_id=f"{source_id}-1", source_id=source_id,
            text="A triangle has three sides and three angles.", ordinal=1,
            page_start=3, page_end=3, metadata={"topic": "geometry"},
        ),
    )


def test_hash_provider_is_deterministic_and_index_normalizes_vectors(tmp_path: Path) -> None:
    provider = HashEmbeddingProvider(dimension=32)
    assert np.array_equal(provider.embed_documents(["same text"]), provider.embed_documents(["same text"]))
    progress: list[IndexProgress] = []

    with SemanticIndex(tmp_path / "metadata.sqlite", provider) as index:
        assert index.index_chunks(make_chunks(), batch_size=1, progress=progress.append) == 2
        assert index.status == "ready"
        assert index.count == 2
        assert [event.completed_chunks for event in progress] == [1, 2]
        results = index.search("equation algebra", top_k=1)

    assert results[0].chunk_id == "source-a-0"
    assert results[0].page_start == 2
    assert results[0].metadata["topic"] == "math"
    assert results[0].score <= 1.00001


def test_reindex_is_idempotent_and_delete_is_source_scoped(tmp_path: Path) -> None:
    provider = HashEmbeddingProvider(dimension=24)
    with SemanticIndex(tmp_path / "metadata.sqlite", provider) as index:
        index.index_chunks(make_chunks("source-a"))
        index.index_chunks(make_chunks("source-a"))
        assert index.count == 2
        index.index_chunks(make_chunks("source-b"))
        assert index.count == 4
        assert index.delete_source("source-a") == 2
        assert index.count == 2
        assert all(result.source_id == "source-b" for result in index.search("algebra", top_k=10))


def test_model_fingerprint_prevents_silent_mixing(tmp_path: Path) -> None:
    database = tmp_path / "metadata.sqlite"
    with SemanticIndex(database, HashEmbeddingProvider(dimension=16, model_name="model-a")) as index:
        index.index_chunks(make_chunks())

    with SemanticIndex(database, HashEmbeddingProvider(dimension=16, model_name="model-b")) as index:
        with pytest.raises(ModelMismatchError):
            index.index_chunks(make_chunks())
        assert index.count == 2
        index.rebuild(make_chunks("source-new"))
        assert index.count == 2
        assert index.search("triangle", top_k=1)[0].source_id == "source-new"


def test_failed_embedding_does_not_create_ready_vectors(tmp_path: Path) -> None:
    class FailingProvider(HashEmbeddingProvider):
        def embed_documents(self, texts):
            raise EmbeddingError("fixture failure")

    with SemanticIndex(tmp_path / "metadata.sqlite", FailingProvider()) as index:
        with pytest.raises(EmbeddingError, match="fixture failure"):
            index.index_chunks(make_chunks())
        assert index.count == 0
        assert index.status == "failed"
        with pytest.raises(IndexStateError):
            index.search("anything")


def test_vector_record_validates_float32_blob_size() -> None:
    with pytest.raises(ValidationError):
        VectorRecord("chunk", "source", "model", 3, b"too short", 1.0, "now")


def test_staged_chunks_are_loaded_and_validated(tmp_path: Path) -> None:
    stage = tmp_path / "stage"
    stage.mkdir()
    (stage / "chunks.jsonl").write_text(
        '{"chunk_id":"c1","source_id":"s1","text":"hello",'
        '"ordinal":0,"metadata":{"source":"fixture"}}\n', encoding="utf-8"
    )
    loaded = load_staged_chunks(stage)
    assert loaded[0].chunk_id == "c1"
    assert loaded[0].metadata["source"] == "fixture"

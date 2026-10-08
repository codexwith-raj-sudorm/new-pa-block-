from __future__ import annotations

from pathlib import Path

import pytest

from study_assistant.contracts import TextChunk
from study_assistant.embeddings import HashEmbeddingProvider, SemanticIndex
from study_assistant.errors import RetrievalError, ValidationError
from study_assistant.generation import ArtifactGenerator, ExtractiveGenerator
from study_assistant.retrieval import HybridRetriever, RetrievalConfig


def fixture_chunks() -> tuple[TextChunk, ...]:
    return (
        TextChunk("c0", "source-a", "The rare term photosynthesis converts light into chemical energy.", 0, page_start=4, metadata={"display_name": "biology.pdf"}),
        TextChunk("c1", "source-a", "Mitochondria release energy for cell activity.", 1, page_start=5, metadata={"display_name": "biology.pdf"}),
        TextChunk("c2", "source-b", "The treaty was signed in 1919 after the conflict.", 0, page_start=2, metadata={"display_name": "history.pdf"}),
    )


def make_retriever(tmp_path: Path) -> HybridRetriever:
    provider = HashEmbeddingProvider(dimension=32)
    database = tmp_path / "index.sqlite"
    with SemanticIndex(database, provider) as index:
        chunks = fixture_chunks()
        index.index_chunks(tuple(chunk for chunk in chunks if chunk.source_id == "source-a"))
        index.index_chunks(tuple(chunk for chunk in chunks if chunk.source_id == "source-b"))
    return HybridRetriever(database, provider, RetrievalConfig(final_candidates=3, context_char_budget=180))


def test_hybrid_retrieval_finds_rare_lexical_term_and_is_stable(tmp_path: Path) -> None:
    with make_retriever(tmp_path) as retriever:
        first = retriever.retrieve("photosynthesis")
        second = retriever.retrieve("photosynthesis")
    assert first.chunks
    assert first.chunks[0].chunk_id == "c0"
    assert first.chunks[0].citation.label() == "biology.pdf | page 4 | chunk c0"
    assert first.context == second.context
    assert len(first.context) <= 180


def test_query_filters_and_empty_queries_are_safe(tmp_path: Path) -> None:
    with make_retriever(tmp_path) as retriever:
        filtered = retriever.retrieve("treaty", source_ids=["source-a"])
        assert filtered.confidence == "missing"
        with pytest.raises(RetrievalError):
            retriever.retrieve("   ")


def test_no_llm_fallback_exposes_evidence_only(tmp_path: Path) -> None:
    with make_retriever(tmp_path) as retriever:
        result = retriever.retrieve("photosynthesis")
    answer = ExtractiveGenerator().answer(result)
    assert answer.provider == "extractive-evidence"
    assert "local language model is not configured" in answer.answer
    assert answer.citations[0].chunk_id == "c0"


def test_artifacts_validate_and_keep_citations(tmp_path: Path) -> None:
    with make_retriever(tmp_path) as retriever:
        result = retriever.retrieve("photosynthesis")
    artifacts = ArtifactGenerator()
    notes = artifacts.notes(result)
    cards = artifacts.flashcards(result)
    quiz = artifacts.quiz(result)
    assert notes.to_markdown().startswith("# Study notes")
    assert cards and cards[0].citations[0].chunk_id == "c0"
    assert quiz.questions and quiz.questions[0].options == ("True", "False")
    with pytest.raises(ValidationError):
        ArtifactGenerator().notes(type("Empty", (), {"query": "x", "chunks": ()})())

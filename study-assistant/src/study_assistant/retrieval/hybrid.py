"""Deterministic SQLite FTS5 plus semantic retrieval."""

from __future__ import annotations

import json
import re
import sqlite3
from pathlib import Path
from typing import Sequence

from ..embeddings import SemanticIndex
from ..embeddings.contracts import EmbeddingProvider, SearchResult
from ..errors import RetrievalError
from .contracts import RetrievalConfig, RetrievalResult, RetrievedChunk


class HybridRetriever:
    """Fuse exact cosine and SQLite FTS5 candidates with reciprocal rank fusion."""

    def __init__(
        self,
        database_path: str | Path,
        provider: EmbeddingProvider,
        config: RetrievalConfig | None = None,
    ) -> None:
        self.database_path = Path(database_path).expanduser()
        self.provider = provider
        self.config = config or RetrievalConfig()
        self._connection = sqlite3.connect(str(self.database_path))
        self._connection.row_factory = sqlite3.Row
        self._connection.execute("PRAGMA foreign_keys = ON")
        self.fts_available = self._create_fts()

    def close(self) -> None:
        self._connection.close()

    def __enter__(self) -> "HybridRetriever":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def refresh(self) -> None:
        """Synchronize the lexical table from the current vector metadata."""

        if not self.fts_available:
            return
        rows = self._connection.execute("SELECT chunk_id, source_id, text FROM vectors").fetchall()
        with self._connection:
            self._connection.execute("DELETE FROM chunk_fts")
            self._connection.executemany(
                "INSERT INTO chunk_fts(chunk_id, source_id, text) VALUES (?, ?, ?)",
                [(row["chunk_id"], row["source_id"], row["text"]) for row in rows],
            )

    def retrieve(
        self,
        query: str,
        *,
        source_ids: Sequence[str] | None = None,
    ) -> RetrievalResult:
        query = " ".join(query.split())
        if not query:
            raise RetrievalError("retrieval query cannot be blank")
        if len(query) > 2000:
            raise RetrievalError("retrieval query is too long; keep it under 2000 characters")

        with SemanticIndex(self.database_path, self.provider) as semantic_index:
            semantic_results = semantic_index.search(
                query,
                top_k=self.config.semantic_candidates,
                source_ids=source_ids,
            )
        self.refresh()
        lexical_results = self._lexical_search(query, source_ids=source_ids)
        fused = self._fuse(semantic_results, lexical_results)
        selected = self._select_diverse(fused)
        selected = self._apply_context_budget(selected)
        context = format_context(selected)
        evidence_quality = any(
            chunk.lexical_score is not None
            or (chunk.semantic_score is not None and chunk.semantic_score >= self.config.min_score)
            for chunk in selected
        )
        if not selected or not evidence_quality:
            confidence = "missing"
        else:
            confidence = "grounded" if len(selected) > 1 or evidence_quality else "weak"
        return RetrievalResult(query=query, chunks=tuple(selected), context=context, confidence=confidence)

    def _create_fts(self) -> bool:
        try:
            with self._connection:
                self._connection.execute(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS chunk_fts USING fts5("
                    "chunk_id UNINDEXED, source_id UNINDEXED, text, tokenize='unicode61')"
                )
            return True
        except sqlite3.OperationalError:
            # Some minimal SQLite builds omit FTS5. Semantic search remains
            # available and Phase 3 transparently degrades to that path.
            return False

    def _lexical_search(
        self,
        query: str,
        *,
        source_ids: Sequence[str] | None = None,
    ) -> list[RetrievedChunk]:
        if not self.fts_available:
            return []
        terms = re.findall(r"[\w-]+", query.casefold(), flags=re.UNICODE)
        if not terms:
            return []
        # Quoted terms avoid FTS operators embedded in study material.
        match = " OR ".join(f'"{term.replace(chr(34), "")}"' for term in terms)
        sql = """
            SELECT f.chunk_id, f.source_id, f.text, bm25(chunk_fts) AS rank,
                   v.ordinal, v.page_start, v.page_end, v.start_seconds, v.end_seconds,
                   v.metadata_json
            FROM chunk_fts AS f
            JOIN vectors AS v ON v.chunk_id = f.chunk_id
            JOIN sources AS s ON s.source_id = f.source_id
            WHERE chunk_fts MATCH ? AND s.status = 'ready'
        """
        params: list[object] = [match]
        if source_ids:
            placeholders = ",".join("?" for _ in source_ids)
            sql += f" AND f.source_id IN ({placeholders})"
            params.extend(source_ids)
        sql += " ORDER BY rank LIMIT ?"
        params.append(self.config.lexical_candidates)
        try:
            rows = self._connection.execute(sql, params).fetchall()
        except sqlite3.OperationalError:
            return []
        results: list[RetrievedChunk] = []
        for row in rows:
            # bm25 returns lower-is-better; rank is retained as a positive
            # lexical signal while RRF itself uses result order.
            lexical_score = 1.0 / (1.0 + max(0.0, float(row["rank"])))
            results.append(
                RetrievedChunk(
                    chunk_id=row["chunk_id"],
                    source_id=row["source_id"],
                    source_name=_source_name(_metadata(row["metadata_json"]), row["source_id"]),
                    text=row["text"],
                    ordinal=int(row["ordinal"]),
                    score=lexical_score,
                    lexical_score=lexical_score,
                    page_start=row["page_start"], page_end=row["page_end"],
                    start_seconds=row["start_seconds"], end_seconds=row["end_seconds"],
                    metadata=_metadata(row["metadata_json"]),
                )
            )
        return results

    def _fuse(
        self,
        semantic_results: Sequence[SearchResult],
        lexical_results: Sequence[RetrievedChunk],
    ) -> list[RetrievedChunk]:
        candidates: dict[str, RetrievedChunk] = {}
        rrf: dict[str, float] = {}
        semantic_by_id = {item.chunk_id: item for item in semantic_results}
        lexical_by_id = {item.chunk_id: item for item in lexical_results}
        for rank, item in enumerate(semantic_results, start=1):
            rrf[item.chunk_id] = rrf.get(item.chunk_id, 0.0) + 1.0 / (self.config.rrf_constant + rank)
            candidates[item.chunk_id] = RetrievedChunk(
                chunk_id=item.chunk_id, source_id=item.source_id,
                source_name=_source_name(item.metadata, item.source_id), text=item.text,
                ordinal=item.ordinal, score=item.score, semantic_score=item.score,
                page_start=item.page_start, page_end=item.page_end,
                start_seconds=item.start_seconds, end_seconds=item.end_seconds,
                metadata=item.metadata,
            )
        for rank, item in enumerate(lexical_results, start=1):
            rrf[item.chunk_id] = rrf.get(item.chunk_id, 0.0) + 1.0 / (self.config.rrf_constant + rank)
            if item.chunk_id not in candidates:
                candidates[item.chunk_id] = item
            else:
                current = candidates[item.chunk_id]
                candidates[item.chunk_id] = RetrievedChunk(
                    chunk_id=current.chunk_id, source_id=current.source_id,
                    source_name=current.source_name, text=current.text,
                    ordinal=current.ordinal, score=current.score,
                    semantic_score=current.semantic_score,
                    lexical_score=item.lexical_score,
                    page_start=current.page_start, page_end=current.page_end,
                    start_seconds=current.start_seconds, end_seconds=current.end_seconds,
                    metadata=current.metadata,
                )
        return sorted(
            [
                RetrievedChunk(
                    chunk_id=item.chunk_id, source_id=item.source_id,
                    source_name=item.source_name, text=item.text, ordinal=item.ordinal,
                    score=rrf[item.chunk_id], semantic_score=item.semantic_score,
                    lexical_score=item.lexical_score, page_start=item.page_start,
                    page_end=item.page_end, start_seconds=item.start_seconds,
                    end_seconds=item.end_seconds, metadata=item.metadata,
                )
                for item in candidates.values()
            ],
            key=lambda item: (-item.score, item.source_id, item.ordinal, item.chunk_id),
        )

    def _select_diverse(self, candidates: Sequence[RetrievedChunk]) -> list[RetrievedChunk]:
        selected: list[RetrievedChunk] = []
        counts: dict[str, int] = {}
        for item in candidates:
            # RRF scores are rank scores, not relevance scores. A semantic-only
            # result must clear the cosine threshold; an exact lexical hit is
            # retained even when its embedding similarity is low.
            if item.lexical_score is None and (
                item.semantic_score is None or item.semantic_score < self.config.min_score
            ):
                continue
            if counts.get(item.source_id, 0) >= self.config.max_per_source:
                continue
            selected.append(item)
            counts[item.source_id] = counts.get(item.source_id, 0) + 1
            if len(selected) >= self.config.final_candidates:
                break
        return selected

    def _apply_context_budget(self, candidates: Sequence[RetrievedChunk]) -> list[RetrievedChunk]:
        selected: list[RetrievedChunk] = []
        used = 0
        for item in candidates:
            block_size = len(item.text) + len(item.citation.label()) + 8
            if not selected and block_size > self.config.context_char_budget:
                text = item.text[: max(1, self.config.context_char_budget - len(item.citation.label()) - 8)]
                selected.append(_with_text(item, text))
                break
            if used + block_size > self.config.context_char_budget:
                break
            selected.append(item)
            used += block_size
        return selected


def format_context(chunks: Sequence[RetrievedChunk]) -> str:
    return "\n\n".join(f"[{chunk.citation.label()}]\n{chunk.text}" for chunk in chunks)


def _metadata(value: str) -> dict[str, str]:
    try:
        parsed = json.loads(value)
        return {str(key): str(item) for key, item in parsed.items()}
    except (TypeError, ValueError, AttributeError):
        return {}


def _source_name(metadata: dict[str, str], fallback: str) -> str:
    return metadata.get("display_name") or metadata.get("source_name") or fallback


def _with_text(item: RetrievedChunk, text: str) -> RetrievedChunk:
    return RetrievedChunk(
        chunk_id=item.chunk_id, source_id=item.source_id, source_name=item.source_name,
        text=text, ordinal=item.ordinal, score=item.score,
        semantic_score=item.semantic_score, lexical_score=item.lexical_score,
        page_start=item.page_start, page_end=item.page_end,
        start_seconds=item.start_seconds, end_seconds=item.end_seconds,
        metadata=item.metadata,
    )

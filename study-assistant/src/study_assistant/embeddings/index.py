"""SQLite-backed exact cosine index for local vectors.

The MVP uses float32 BLOBs rather than a hosted vector database. Metadata and
vectors are committed together, so a source is only searchable after every
chunk in its build has a validated vector.
"""

from __future__ import annotations

import json
import sqlite3
import time
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Callable, Iterable, Sequence

import numpy as np

from ..contracts import TextChunk
from ..errors import EmbeddingError, IndexStateError, IndexingError, ModelMismatchError, ValidationError
from .contracts import EmbeddingProvider, IndexProgress, SearchResult, VectorRecord

ProgressCallback = Callable[[IndexProgress], None]


class SemanticIndex:
    """A small local exact-search index with model compatibility guardrails."""

    SCHEMA_VERSION = "1"

    def __init__(self, database_path: str | Path, provider: EmbeddingProvider) -> None:
        self.database_path = Path(database_path).expanduser()
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        self.provider = provider
        self._connection = sqlite3.connect(str(self.database_path))
        self._connection.row_factory = sqlite3.Row
        self._connection.execute("PRAGMA foreign_keys = ON")
        self._connection.execute("PRAGMA journal_mode = WAL")
        self._connection.execute("PRAGMA synchronous = NORMAL")
        self._create_schema()

    def close(self) -> None:
        self._connection.close()

    def __enter__(self) -> "SemanticIndex":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    @property
    def status(self) -> str:
        return self._meta("status") or "empty"

    @property
    def model_fingerprint(self) -> str | None:
        return self._meta("model_fingerprint")

    @property
    def count(self) -> int:
        row = self._connection.execute("SELECT COUNT(*) AS count FROM vectors").fetchone()
        return int(row["count"])

    def index_chunks(
        self,
        chunks: Iterable[TextChunk],
        *,
        batch_size: int = 32,
        rebuild_on_mismatch: bool = False,
        progress: ProgressCallback | None = None,
    ) -> int:
        """Embed and atomically index all chunks from one source.

        Embeddings are computed before the write transaction. If a provider
        fails, no new vectors are written and the source is marked failed. A
        source re-index replaces its prior rows rather than duplicating them.
        """

        chunk_list = tuple(chunks)
        if not chunk_list:
            raise ValidationError("cannot index an empty chunk collection")
        if batch_size < 1:
            raise ValidationError("batch_size must be positive")
        source_ids = {chunk.source_id for chunk in chunk_list}
        if len(source_ids) != 1:
            raise ValidationError("one index operation must contain exactly one source")
        source_id = next(iter(source_ids))
        self._ensure_provider_compatible(rebuild_on_mismatch=rebuild_on_mismatch)
        # Mark the source and index as building before model work starts. This
        # prevents concurrent readers from treating stale rows as ready while
        # a source is being replaced.
        self._mark_building(source_id, len(chunk_list))
        started = time.monotonic()
        prepared: list[tuple[TextChunk, np.ndarray, float]] = []
        try:
            for offset in range(0, len(chunk_list), batch_size):
                batch = chunk_list[offset : offset + batch_size]
                raw_vectors = self.provider.embed_documents([chunk.text for chunk in batch])
                matrix = _validate_matrix(raw_vectors, len(batch), self.provider.dimension)
                for chunk, raw_vector in zip(batch, matrix, strict=True):
                    normalized, norm = _normalize(raw_vector)
                    prepared.append((chunk, normalized, norm))
                if progress:
                    progress(
                        IndexProgress(
                            source_id=source_id,
                            completed_chunks=len(prepared),
                            total_chunks=len(chunk_list),
                            elapsed_seconds=time.monotonic() - started,
                        )
                    )
        except Exception as exc:
            self._mark_failed(source_id, str(exc))
            if isinstance(exc, (EmbeddingError, ValidationError)):
                raise
            raise EmbeddingError(f"embedding failed for source {source_id}: {exc}") from exc

        try:
            self._write_source(source_id, chunk_list, prepared)
        except Exception as exc:
            self._mark_failed(source_id, str(exc))
            if isinstance(exc, IndexingError):
                raise
            raise IndexingError(f"could not write vectors for source {source_id}: {exc}") from exc
        return len(prepared)

    def index_staged(
        self,
        stage_directory: str | Path,
        *,
        batch_size: int = 32,
        rebuild_on_mismatch: bool = False,
        progress: ProgressCallback | None = None,
    ) -> int:
        """Index the validated ``chunks.jsonl`` emitted by Phase 1."""

        return self.index_chunks(
            load_staged_chunks(stage_directory),
            batch_size=batch_size,
            rebuild_on_mismatch=rebuild_on_mismatch,
            progress=progress,
        )

    def clear(self) -> None:
        """Remove all vectors and reset this database for the active provider."""

        with self._connection:
            self._connection.execute("DELETE FROM vectors")
            self._connection.execute("DELETE FROM sources")
            self._set_meta_in_transaction("model_name", self.provider.model_name)
            self._set_meta_in_transaction("model_fingerprint", self.provider.fingerprint)
            self._set_meta_in_transaction("dimension", str(self.provider.dimension))
            self._set_status_in_transaction("empty")

    def rebuild(
        self,
        chunks: Iterable[TextChunk],
        *,
        batch_size: int = 32,
        progress: ProgressCallback | None = None,
    ) -> int:
        """Explicitly clear the existing model index and build from chunks."""

        self.clear()
        return self.index_chunks(
            chunks,
            batch_size=batch_size,
            rebuild_on_mismatch=True,
            progress=progress,
        )

    def search(
        self,
        query: str,
        *,
        top_k: int = 5,
        source_ids: Sequence[str] | None = None,
    ) -> tuple[SearchResult, ...]:
        if not query.strip():
            raise ValidationError("search query cannot be blank")
        if top_k < 1:
            raise ValidationError("top_k must be positive")
        self._ensure_provider_compatible(rebuild_on_mismatch=False)
        if self.status == "empty":
            return ()
        if self.status != "ready":
            raise IndexStateError(f"index is {self.status}, not ready for search")

        query_vector = _normalize_query(self.provider.embed_query(query), self.provider.dimension)
        sql = """
            SELECT v.chunk_id, v.source_id, v.text, v.ordinal,
                   v.page_start, v.page_end, v.start_seconds, v.end_seconds,
                   v.metadata_json, v.vector
            FROM vectors AS v
            JOIN sources AS s ON s.source_id = v.source_id
            WHERE s.status = 'ready'
        """
        params: list[object] = []
        if source_ids:
            placeholders = ",".join("?" for _ in source_ids)
            sql += f" AND v.source_id IN ({placeholders})"
            params.extend(source_ids)
        rows = self._connection.execute(sql, params).fetchall()
        scored: list[SearchResult] = []
        for row in rows:
            vector = _blob_to_vector(row["vector"], self.provider.dimension)
            score = float(np.dot(query_vector, vector))
            metadata = json.loads(row["metadata_json"])
            scored.append(
                SearchResult(
                    chunk_id=row["chunk_id"],
                    source_id=row["source_id"],
                    text=row["text"],
                    score=score,
                    ordinal=int(row["ordinal"]),
                    page_start=row["page_start"],
                    page_end=row["page_end"],
                    start_seconds=row["start_seconds"],
                    end_seconds=row["end_seconds"],
                    metadata={str(key): str(value) for key, value in metadata.items()},
                )
            )
        scored.sort(key=lambda result: (-result.score, result.source_id, result.ordinal, result.chunk_id))
        return tuple(scored[:top_k])

    def delete_source(self, source_id: str) -> int:
        if not source_id.strip():
            raise ValidationError("source_id cannot be blank")
        with self._connection:
            row = self._connection.execute(
                "SELECT COUNT(*) AS count FROM vectors WHERE source_id = ?", (source_id,)
            ).fetchone()
            deleted = int(row["count"])
            self._connection.execute("DELETE FROM sources WHERE source_id = ?", (source_id,))
            self._set_status_in_transaction("ready" if self._all_sources_ready() else "empty")
        return deleted

    def source_status(self, source_id: str) -> dict[str, object] | None:
        row = self._connection.execute(
            """
            SELECT source_id, status, model_name, model_fingerprint, dimension,
                   chunk_count, indexed_count, error, updated_at
            FROM sources WHERE source_id = ?
            """,
            (source_id,),
        ).fetchone()
        return dict(row) if row else None

    def _create_schema(self) -> None:
        with self._connection:
            self._connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS index_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS sources (
                    source_id TEXT PRIMARY KEY,
                    status TEXT NOT NULL CHECK(status IN ('building', 'ready', 'failed')),
                    model_name TEXT NOT NULL,
                    model_fingerprint TEXT NOT NULL,
                    dimension INTEGER NOT NULL,
                    chunk_count INTEGER NOT NULL DEFAULT 0,
                    indexed_count INTEGER NOT NULL DEFAULT 0,
                    error TEXT,
                    updated_at TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS vectors (
                    chunk_id TEXT PRIMARY KEY,
                    source_id TEXT NOT NULL REFERENCES sources(source_id) ON DELETE CASCADE,
                    ordinal INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    text_hash TEXT NOT NULL,
                    page_start INTEGER,
                    page_end INTEGER,
                    start_seconds REAL,
                    end_seconds REAL,
                    metadata_json TEXT NOT NULL,
                    model_name TEXT NOT NULL,
                    model_fingerprint TEXT NOT NULL,
                    dimension INTEGER NOT NULL,
                    vector BLOB NOT NULL,
                    norm REAL NOT NULL,
                    created_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS vectors_source_idx ON vectors(source_id, ordinal);
                """
            )
        if self._meta("schema_version") is None:
            with self._connection:
                self._set_meta_in_transaction("schema_version", self.SCHEMA_VERSION)
                self._set_meta_in_transaction("status", "empty")
                self._set_meta_in_transaction("model_name", self.provider.model_name)
                self._set_meta_in_transaction("model_fingerprint", self.provider.fingerprint)
                self._set_meta_in_transaction("dimension", str(self.provider.dimension))

    def _ensure_provider_compatible(self, *, rebuild_on_mismatch: bool) -> None:
        stored_fingerprint = self._meta("model_fingerprint")
        stored_name = self._meta("model_name")
        stored_dimension = self._meta("dimension")
        if stored_fingerprint is None:
            with self._connection:
                self._set_meta_in_transaction("model_name", self.provider.model_name)
                self._set_meta_in_transaction("model_fingerprint", self.provider.fingerprint)
                self._set_meta_in_transaction("dimension", str(self.provider.dimension))
            return
        mismatch = (
            stored_fingerprint != self.provider.fingerprint
            or stored_dimension != str(self.provider.dimension)
        )
        if not mismatch:
            return
        if not rebuild_on_mismatch:
            raise ModelMismatchError(
                "embedding model/configuration does not match this index; "
                "call rebuild() or pass rebuild_on_mismatch=True"
            )
        with self._connection:
            self._connection.execute("DELETE FROM vectors")
            self._connection.execute("DELETE FROM sources")
            self._set_meta_in_transaction("model_name", self.provider.model_name)
            self._set_meta_in_transaction("model_fingerprint", self.provider.fingerprint)
            self._set_meta_in_transaction("dimension", str(self.provider.dimension))
            self._set_status_in_transaction("building")

    def _write_source(
        self,
        source_id: str,
        chunks: Sequence[TextChunk],
        prepared: Sequence[tuple[TextChunk, np.ndarray, float]],
    ) -> None:
        now = _utc_now()
        with self._connection:
            self._connection.execute("DELETE FROM vectors WHERE source_id = ?", (source_id,))
            self._connection.execute("DELETE FROM sources WHERE source_id = ?", (source_id,))
            self._connection.execute(
                """
                INSERT INTO sources(
                    source_id, status, model_name, model_fingerprint, dimension,
                    chunk_count, indexed_count, error, updated_at
                ) VALUES (?, 'building', ?, ?, ?, ?, 0, NULL, ?)
                """,
                (
                    source_id,
                    self.provider.model_name,
                    self.provider.fingerprint,
                    self.provider.dimension,
                    len(chunks),
                    now,
                ),
            )
            records: list[VectorRecord] = []
            for chunk, normalized, norm in prepared:
                blob = normalized.astype(np.float32, copy=False).tobytes(order="C")
                records.append(
                    VectorRecord(
                        chunk_id=chunk.chunk_id,
                        source_id=source_id,
                        model_name=self.provider.model_name,
                        dimension=self.provider.dimension,
                        vector=blob,
                        norm=norm,
                        created_at=now,
                        fingerprint=self.provider.fingerprint,
                    )
                )
                self._connection.execute(
                    """
                    INSERT INTO vectors(
                        chunk_id, source_id, ordinal, text, text_hash,
                        page_start, page_end, start_seconds, end_seconds,
                        metadata_json, model_name, model_fingerprint, dimension,
                        vector, norm, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        chunk.chunk_id,
                        source_id,
                        chunk.ordinal,
                        chunk.text,
                        sha256(chunk.text.encode("utf-8")).hexdigest(),
                        chunk.page_start,
                        chunk.page_end,
                        chunk.start_seconds,
                        chunk.end_seconds,
                        json.dumps(chunk.metadata, ensure_ascii=False, sort_keys=True),
                        self.provider.model_name,
                        self.provider.fingerprint,
                        self.provider.dimension,
                        blob,
                        norm,
                        now,
                    ),
                )
            # Constructing VectorRecords above validates every blob before the
            # source becomes ready, even though only the compact row is stored.
            self._connection.execute(
                """
                UPDATE sources
                SET status = 'ready', indexed_count = ?, error = NULL, updated_at = ?
                WHERE source_id = ?
                """,
                (len(records), now, source_id),
            )
            self._set_status_in_transaction("ready")

    def _mark_building(self, source_id: str, chunk_count: int) -> None:
        now = _utc_now()
        with self._connection:
            self._connection.execute(
                """
                INSERT INTO sources(
                    source_id, status, model_name, model_fingerprint, dimension,
                    chunk_count, indexed_count, error, updated_at
                ) VALUES (?, 'building', ?, ?, ?, ?, 0, NULL, ?)
                ON CONFLICT(source_id) DO UPDATE SET
                    status = 'building', model_name = excluded.model_name,
                    model_fingerprint = excluded.model_fingerprint,
                    dimension = excluded.dimension, chunk_count = excluded.chunk_count,
                    error = NULL, updated_at = excluded.updated_at
                """,
                (
                    source_id,
                    self.provider.model_name,
                    self.provider.fingerprint,
                    self.provider.dimension,
                    chunk_count,
                    now,
                ),
            )
            self._set_status_in_transaction("building")

    def _mark_failed(self, source_id: str, error: str) -> None:
        now = _utc_now()
        try:
            with self._connection:
                self._connection.execute(
                    """
                    INSERT INTO sources(
                        source_id, status, model_name, model_fingerprint, dimension,
                        chunk_count, indexed_count, error, updated_at
                    ) VALUES (?, 'failed', ?, ?, ?, 0, 0, ?, ?)
                    ON CONFLICT(source_id) DO UPDATE SET
                        status = 'failed', error = excluded.error, updated_at = excluded.updated_at
                    """,
                    (
                        source_id,
                        self.provider.model_name,
                        self.provider.fingerprint,
                        self.provider.dimension,
                        error[:2000],
                        now,
                    ),
                )
                self._set_status_in_transaction("failed")
        except sqlite3.Error:
            # Preserve the original embedding/write error when a diagnostic
            # status update itself cannot be persisted.
            pass

    def _all_sources_ready(self) -> bool:
        row = self._connection.execute(
            "SELECT COUNT(*) AS total, SUM(status = 'ready') AS ready FROM sources"
        ).fetchone()
        return int(row["total"] or 0) > 0 and int(row["total"] or 0) == int(row["ready"] or 0)

    def _meta(self, key: str) -> str | None:
        row = self._connection.execute("SELECT value FROM index_meta WHERE key = ?", (key,)).fetchone()
        return str(row["value"]) if row else None

    def _set_meta_in_transaction(self, key: str, value: str) -> None:
        self._connection.execute(
            "INSERT INTO index_meta(key, value) VALUES (?, ?) "
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )

    def _set_status_in_transaction(self, status: str) -> None:
        self._set_meta_in_transaction("status", status)


def load_staged_chunks(stage_directory: str | Path) -> tuple[TextChunk, ...]:
    """Load and validate the JSONL output produced by Phase 1."""

    path = Path(stage_directory).expanduser() / "chunks.jsonl"
    if not path.is_file():
        raise IndexingError(f"staged chunk file does not exist: {path}")
    chunks: list[TextChunk] = []
    line_number = 0
    try:
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not line.strip():
                continue
            data = json.loads(line)
            chunks.append(
                TextChunk(
                    chunk_id=str(data["chunk_id"]),
                    source_id=str(data["source_id"]),
                    text=str(data["text"]),
                    ordinal=int(data["ordinal"]),
                    start_seconds=_optional_float(data.get("start_seconds")),
                    end_seconds=_optional_float(data.get("end_seconds")),
                    page_start=_optional_int(data.get("page_start")),
                    page_end=_optional_int(data.get("page_end")),
                    metadata={str(key): str(value) for key, value in data.get("metadata", {}).items()},
                )
            )
    except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        raise IndexingError(f"invalid staged chunk on line {line_number}: {exc}") from exc
    if not chunks:
        raise IndexingError(f"staged chunk file is empty: {path}")
    return tuple(chunks)


def _validate_matrix(value: object, expected_rows: int, expected_dimension: int) -> np.ndarray:
    try:
        matrix = np.asarray(value, dtype=np.float32)
    except (TypeError, ValueError) as exc:
        raise EmbeddingError(f"embedding output is not numeric: {exc}") from exc
    if matrix.ndim == 1:
        matrix = matrix.reshape(1, -1)
    if matrix.shape != (expected_rows, expected_dimension):
        raise EmbeddingError(
            f"embedding shape {matrix.shape} does not match "
            f"({expected_rows}, {expected_dimension})"
        )
    if not np.isfinite(matrix).all():
        raise EmbeddingError("embedding output contains NaN or infinity")
    return np.ascontiguousarray(matrix, dtype=np.float32)


def _normalize(vector: np.ndarray) -> tuple[np.ndarray, float]:
    norm = float(np.linalg.norm(vector))
    if not np.isfinite(norm) or norm <= 0:
        raise EmbeddingError("embedding vector has zero or invalid L2 norm")
    return np.ascontiguousarray(vector / norm, dtype=np.float32), norm


def _normalize_query(value: object, dimension: int) -> np.ndarray:
    matrix = _validate_matrix(value, 1, dimension)
    return _normalize(matrix[0])[0]


def _blob_to_vector(value: bytes, dimension: int) -> np.ndarray:
    expected = dimension * 4
    if len(value) != expected:
        raise IndexingError(f"stored vector has {len(value)} bytes; expected {expected}")
    vector = np.frombuffer(value, dtype=np.float32)
    norm = float(np.linalg.norm(vector))
    if not np.isfinite(norm) or norm <= 0:
        raise IndexingError("stored vector has an invalid norm")
    return vector


def _optional_int(value: object) -> int | None:
    return None if value is None else int(value)


def _optional_float(value: object) -> float | None:
    return None if value is None else float(value)


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")

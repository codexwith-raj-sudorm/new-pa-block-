"""Provider-neutral records for local embeddings and semantic search."""

from __future__ import annotations

from dataclasses import dataclass, field
from math import isfinite
from typing import Any, Protocol, Sequence

from ..errors import ValidationError


class EmbeddingProvider(Protocol):
    """The minimal interface required by the local index."""

    @property
    def model_name(self) -> str: ...

    @property
    def dimension(self) -> int: ...

    @property
    def fingerprint(self) -> str: ...

    def embed_documents(self, texts: Sequence[str]) -> Any: ...

    def embed_query(self, text: str) -> Any: ...


@dataclass(frozen=True)
class VectorRecord:
    chunk_id: str
    source_id: str
    model_name: str
    dimension: int
    vector: bytes
    norm: float
    created_at: str
    fingerprint: str = ""

    def __post_init__(self) -> None:
        if not self.chunk_id.strip() or not self.source_id.strip():
            raise ValidationError("vector chunk_id and source_id cannot be empty")
        if not self.model_name.strip():
            raise ValidationError("vector model_name cannot be empty")
        if self.dimension < 1:
            raise ValidationError("vector dimension must be positive")
        if self.norm <= 0 or not isfinite(self.norm):
            raise ValidationError("vector norm must be finite and positive")
        expected_bytes = self.dimension * 4
        if len(self.vector) != expected_bytes:
            raise ValidationError(
                f"vector blob has {len(self.vector)} bytes; expected {expected_bytes}"
            )

    def as_dict(self) -> dict[str, Any]:
        return {
            "chunk_id": self.chunk_id,
            "source_id": self.source_id,
            "model_name": self.model_name,
            "dimension": self.dimension,
            "norm": self.norm,
            "created_at": self.created_at,
            "fingerprint": self.fingerprint,
        }


@dataclass(frozen=True)
class IndexProgress:
    source_id: str
    completed_chunks: int
    total_chunks: int
    elapsed_seconds: float
    stage: str = "embedding"

    def __post_init__(self) -> None:
        if not self.source_id.strip():
            raise ValidationError("progress source_id cannot be empty")
        if self.total_chunks < 1 or not 0 <= self.completed_chunks <= self.total_chunks:
            raise ValidationError("progress chunk counts are invalid")
        if self.elapsed_seconds < 0:
            raise ValidationError("progress elapsed time cannot be negative")


@dataclass(frozen=True)
class SearchResult:
    chunk_id: str
    source_id: str
    text: str
    score: float
    ordinal: int
    page_start: int | None = None
    page_end: int | None = None
    start_seconds: float | None = None
    end_seconds: float | None = None
    metadata: dict[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.chunk_id.strip() or not self.source_id.strip():
            raise ValidationError("search result IDs cannot be empty")
        if not self.text.strip():
            raise ValidationError("search result text cannot be empty")
        if not isfinite(self.score):
            raise ValidationError("search score must be finite")

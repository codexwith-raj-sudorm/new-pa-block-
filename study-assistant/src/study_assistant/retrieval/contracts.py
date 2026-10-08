"""Validated records shared by retrieval and generation."""

from __future__ import annotations

from dataclasses import dataclass, field
from math import isfinite

from ..errors import ValidationError


@dataclass(frozen=True)
class Citation:
    chunk_id: str
    source_name: str
    page_start: int | None = None
    page_end: int | None = None
    start_seconds: float | None = None
    end_seconds: float | None = None

    def __post_init__(self) -> None:
        if not self.chunk_id.strip() or not self.source_name.strip():
            raise ValidationError("citation chunk_id and source_name cannot be empty")
        if self.page_start is not None and self.page_start < 1:
            raise ValidationError("citation page_start must be positive")
        if self.page_end is not None and self.page_end < 1:
            raise ValidationError("citation page_end must be positive")
        if self.start_seconds is not None and self.start_seconds < 0:
            raise ValidationError("citation start_seconds cannot be negative")
        if self.end_seconds is not None and self.end_seconds < 0:
            raise ValidationError("citation end_seconds cannot be negative")
        if (
            self.start_seconds is not None
            and self.end_seconds is not None
            and self.end_seconds < self.start_seconds
        ):
            raise ValidationError("citation end_seconds cannot precede start_seconds")

    def as_dict(self) -> dict[str, object]:
        return {
            "chunk_id": self.chunk_id,
            "source_name": self.source_name,
            "page_start": self.page_start,
            "page_end": self.page_end,
            "start_seconds": self.start_seconds,
            "end_seconds": self.end_seconds,
        }

    def label(self) -> str:
        if self.page_start is not None:
            page_end = self.page_end if self.page_end is not None else self.page_start
            page = str(self.page_start) if self.page_start == page_end else f"{self.page_start}-{page_end}"
            return f"{self.source_name} | page {page} | chunk {self.chunk_id}"
        if self.start_seconds is not None:
            start = _format_seconds(self.start_seconds)
            end = _format_seconds(self.end_seconds) if self.end_seconds is not None else "?"
            return f"{self.source_name} | {start}-{end} | chunk {self.chunk_id}"
        return f"{self.source_name} | chunk {self.chunk_id}"


@dataclass(frozen=True)
class RetrievedChunk:
    chunk_id: str
    source_id: str
    source_name: str
    text: str
    ordinal: int
    score: float
    semantic_score: float | None = None
    lexical_score: float | None = None
    page_start: int | None = None
    page_end: int | None = None
    start_seconds: float | None = None
    end_seconds: float | None = None
    metadata: dict[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.chunk_id.strip() or not self.source_id.strip() or not self.source_name.strip():
            raise ValidationError("retrieved chunk identifiers cannot be empty")
        if not self.text.strip():
            raise ValidationError("retrieved chunk text cannot be empty")
        if not isfinite(self.score):
            raise ValidationError("retrieved chunk score must be finite")

    @property
    def citation(self) -> Citation:
        return Citation(
            chunk_id=self.chunk_id,
            source_name=self.source_name,
            page_start=self.page_start,
            page_end=self.page_end,
            start_seconds=self.start_seconds,
            end_seconds=self.end_seconds,
        )


@dataclass(frozen=True)
class RetrievalConfig:
    semantic_candidates: int = 12
    lexical_candidates: int = 12
    final_candidates: int = 5
    rrf_constant: int = 60
    context_char_budget: int = 6000
    min_score: float = 0.25
    max_per_source: int = 3

    def __post_init__(self) -> None:
        if min(self.semantic_candidates, self.lexical_candidates, self.final_candidates) < 1:
            raise ValidationError("retrieval candidate counts must be positive")
        if self.rrf_constant < 1 or self.context_char_budget < 1 or self.max_per_source < 1:
            raise ValidationError("retrieval limits must be positive")


@dataclass(frozen=True)
class RetrievalResult:
    query: str
    chunks: tuple[RetrievedChunk, ...]
    context: str
    confidence: str

    def __post_init__(self) -> None:
        if not self.query.strip():
            raise ValidationError("retrieval query cannot be empty")
        if self.confidence not in {"grounded", "weak", "missing"}:
            raise ValidationError("invalid retrieval confidence")


def _format_seconds(value: float | None) -> str:
    if value is None:
        return "?"
    seconds = max(0, int(value))
    minutes, remainder = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{hours:02d}:{minutes:02d}:{remainder:02d}" if hours else f"{minutes:02d}:{remainder:02d}"

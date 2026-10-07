"""Small, provider-neutral records shared by all phases.

The records intentionally remain dataclasses instead of depending on a web
framework or an LLM SDK. They are the stable boundary between Phase 1 and
later local providers.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from hashlib import sha256
from typing import Any, Literal

from .errors import ValidationError

SourceType = Literal["pdf", "audio", "youtube"]


def stable_id(*parts: object, length: int = 24) -> str:
    """Return a short, deterministic identifier for non-secret source data."""

    payload = "\x1f".join(str(part) for part in parts)
    return sha256(payload.encode("utf-8")).hexdigest()[:length]


def _validate_time_pair(start: float | None, end: float | None) -> None:
    if start is not None and start < 0:
        raise ValidationError("start time cannot be negative")
    if end is not None and end < 0:
        raise ValidationError("end time cannot be negative")
    if start is not None and end is not None and end < start:
        raise ValidationError("end time cannot be before start time")


@dataclass(frozen=True)
class SourceRef:
    source_id: str
    source_type: SourceType
    original_uri: str
    display_name: str
    checksum: str | None = None

    def __post_init__(self) -> None:
        if not self.source_id.strip():
            raise ValidationError("source_id cannot be empty")
        if self.source_type not in {"pdf", "audio", "youtube"}:
            raise ValidationError(f"unsupported source type: {self.source_type}")
        if not self.original_uri.strip():
            raise ValidationError("original_uri cannot be empty")
        if not self.display_name.strip():
            raise ValidationError("display_name cannot be empty")
        if self.checksum is not None and not self.checksum.strip():
            raise ValidationError("checksum cannot be blank")


@dataclass(frozen=True)
class TranscriptSegment:
    text: str
    start_seconds: float | None = None
    end_seconds: float | None = None
    page_number: int | None = None

    def __post_init__(self) -> None:
        if not self.text.strip():
            raise ValidationError("transcript segment text cannot be empty")
        _validate_time_pair(self.start_seconds, self.end_seconds)
        if self.page_number is not None and self.page_number < 1:
            raise ValidationError("page_number must be positive")


@dataclass(frozen=True)
class IngestionDocument:
    source: SourceRef
    title: str
    text: str
    language: str | None = None
    duration_seconds: float | None = None
    page_count: int | None = None
    metadata: dict[str, str] = field(default_factory=dict)
    segments: tuple[TranscriptSegment, ...] = ()

    def __post_init__(self) -> None:
        if not self.title.strip():
            raise ValidationError("document title cannot be empty")
        if not self.text.strip():
            raise ValidationError("document text cannot be empty")
        if self.duration_seconds is not None and self.duration_seconds < 0:
            raise ValidationError("duration_seconds cannot be negative")
        if self.page_count is not None and self.page_count < 1:
            raise ValidationError("page_count must be positive")

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class TextChunk:
    chunk_id: str
    source_id: str
    text: str
    ordinal: int
    start_seconds: float | None = None
    end_seconds: float | None = None
    page_start: int | None = None
    page_end: int | None = None
    metadata: dict[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.chunk_id.strip() or not self.source_id.strip():
            raise ValidationError("chunk_id and source_id cannot be empty")
        if not self.text.strip():
            raise ValidationError("chunk text cannot be empty")
        if self.ordinal < 0:
            raise ValidationError("chunk ordinal cannot be negative")
        _validate_time_pair(self.start_seconds, self.end_seconds)
        if self.page_start is not None and self.page_start < 1:
            raise ValidationError("page_start must be positive")
        if self.page_end is not None and self.page_end < 1:
            raise ValidationError("page_end must be positive")
        if (
            self.page_start is not None
            and self.page_end is not None
            and self.page_end < self.page_start
        ):
            raise ValidationError("page_end cannot be before page_start")

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class MediaAsset:
    path: str
    source_type: SourceType
    original_uri: str
    display_name: str
    source_id: str
    checksum: str | None = None
    duration_seconds: float | None = None
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class TranscriptionResult:
    segments: tuple[TranscriptSegment, ...]
    language: str | None = None
    duration_seconds: float | None = None


@dataclass(frozen=True)
class ProgressEvent:
    stage: str
    message: str
    fraction: float | None = None

    def __post_init__(self) -> None:
        if self.fraction is not None and not 0 <= self.fraction <= 1:
            raise ValidationError("progress fraction must be between 0 and 1")

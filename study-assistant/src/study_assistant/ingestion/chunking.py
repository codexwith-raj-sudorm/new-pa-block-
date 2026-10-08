"""Deterministic, provenance-aware recursive chunking."""

from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from ..contracts import IngestionDocument, TextChunk, TranscriptSegment, stable_id
from ..errors import ValidationError


@dataclass(frozen=True)
class ChunkingConfig:
    # Character limits keep Phase 1 provider-neutral. Phase 2 can convert to
    # tokenizer-aware limits without changing the TextChunk contract.
    chunk_size_chars: int = 800
    chunk_overlap_chars: int = 120

    def __post_init__(self) -> None:
        if self.chunk_size_chars < 1:
            raise ValidationError("chunk_size_chars must be positive")
        if not 0 <= self.chunk_overlap_chars < self.chunk_size_chars:
            raise ValidationError(
                "chunk_overlap_chars must be non-negative and smaller than chunk_size_chars"
            )

    @property
    def fingerprint(self) -> str:
        return f"recursive-v1:{self.chunk_size_chars}:{self.chunk_overlap_chars}"


class DeterministicChunker:
    """Split the normalized document while retaining source coordinate ranges."""

    def __init__(self, config: ChunkingConfig | None = None) -> None:
        self.config = config or ChunkingConfig()

    def chunk(self, document: IngestionDocument) -> tuple[TextChunk, ...]:
        parts = self._split(document.text)
        spans = self._segment_spans(document)
        chunks: list[TextChunk] = []
        search_from = 0
        for ordinal, part in enumerate(parts):
            # Splitting libraries return the content without boundary padding.
            # Searching from the prior start handles overlap and remains stable
            # for repeated phrases by selecting the first valid occurrence.
            start = document.text.find(part, max(0, search_from - self.config.chunk_overlap_chars))
            if start < 0:
                start = search_from
            end = start + len(part)
            search_from = max(start + 1, end - self.config.chunk_overlap_chars)
            matching = [span for span in spans if span[1] > start and span[0] < end]

            page_values = [span[2].page_number for span in matching if span[2].page_number]
            starts = [span[2].start_seconds for span in matching if span[2].start_seconds is not None]
            ends = [span[2].end_seconds for span in matching if span[2].end_seconds is not None]
            metadata = {
                "chunker": "recursive-v1",
                "chunk_config": self.config.fingerprint,
                "source_type": document.source.source_type,
                "display_name": document.source.display_name,
            }
            chunk_id = stable_id(
                document.source.source_id,
                ordinal,
                sha256(part.encode("utf-8")).hexdigest(),
                self.config.fingerprint,
            )
            chunks.append(
                TextChunk(
                    chunk_id=chunk_id,
                    source_id=document.source.source_id,
                    text=part,
                    ordinal=ordinal,
                    start_seconds=min(starts) if starts else None,
                    end_seconds=max(ends) if ends else None,
                    page_start=min(page_values) if page_values else None,
                    page_end=max(page_values) if page_values else None,
                    metadata=metadata,
                )
            )
        return tuple(chunks)

    def _split(self, text: str) -> list[str]:
        """Use LangChain's splitter when installed, with a stdlib fallback.

        The fallback makes contract tests and PDF-only installs usable without
        importing an optional package. Both use the same recursive separators.
        """

        try:
            from langchain_text_splitters import RecursiveCharacterTextSplitter

            splitter = RecursiveCharacterTextSplitter(
                chunk_size=self.config.chunk_size_chars,
                chunk_overlap=self.config.chunk_overlap_chars,
                length_function=len,
                separators=["\n\n", "\n", " ", ""],
                strip_whitespace=True,
            )
            result = splitter.split_text(text)
        except ImportError:  # pragma: no cover - exercised only in minimal envs
            result = self._fallback_split(text)
        return [part for part in result if part.strip()]

    def _fallback_split(self, text: str) -> list[str]:
        result: list[str] = []
        start = 0
        while start < len(text):
            proposed_end = min(len(text), start + self.config.chunk_size_chars)
            end = proposed_end
            if proposed_end < len(text):
                boundary = max(
                    text.rfind("\n\n", start, proposed_end),
                    text.rfind("\n", start, proposed_end),
                    text.rfind(" ", start, proposed_end),
                )
                if boundary > start:
                    end = boundary
            part = text[start:end].strip()
            if part:
                result.append(part)
            if end >= len(text):
                break
            next_start = max(end - self.config.chunk_overlap_chars, start + 1)
            while next_start < len(text) and text[next_start].isspace():
                next_start += 1
            start = next_start
        return result

    @staticmethod
    def _segment_spans(
        document: IngestionDocument,
    ) -> list[tuple[int, int, TranscriptSegment]]:
        spans: list[tuple[int, int, TranscriptSegment]] = []
        cursor = 0
        for segment in document.segments:
            position = document.text.find(segment.text, cursor)
            if position < 0:
                # A provider may normalize a segment slightly differently.
                # Leaving it unmapped is safer than assigning false provenance.
                continue
            end = position + len(segment.text)
            spans.append((position, end, segment))
            cursor = end
        return spans

"""Phase 1 orchestration without provider-specific return values."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from ..contracts import (
    IngestionDocument,
    MediaAsset,
    ProgressEvent,
    SourceRef,
    TranscriptSegment,
    TextChunk,
)
from ..environment import collect_environment_metadata
from ..errors import IngestionError
from .chunking import DeterministicChunker
from .media import MediaLoader
from .normalize import normalize_text
from .pdf import PdfIngestor
from .staging import stage_records
from .transcription import WhisperTranscriber

ProgressCallback = Callable[[ProgressEvent], None]


@dataclass(frozen=True)
class IngestionResult:
    document: IngestionDocument
    chunks: tuple[TextChunk, ...]
    staging_dir: Path


class IngestionService:
    """Load one source, normalize it, chunk it, and stage the result."""

    def __init__(
        self,
        output_root: str | Path = "var/staging",
        *,
        pdf_ingestor: PdfIngestor | None = None,
        media_loader: MediaLoader | None = None,
        transcriber: WhisperTranscriber | None = None,
        chunker: DeterministicChunker | None = None,
        progress: ProgressCallback | None = None,
    ) -> None:
        self.output_root = Path(output_root).expanduser()
        self.pdf_ingestor = pdf_ingestor or PdfIngestor()
        self.media_loader = media_loader or MediaLoader(self.output_root)
        self.transcriber = transcriber or WhisperTranscriber()
        self.chunker = chunker or DeterministicChunker()
        self.progress = progress

    def ingest_pdf(self, path: str | Path) -> IngestionResult:
        self._emit("load", "Extracting PDF text")
        document = self.pdf_ingestor.load(path)
        return self._finalize(document)

    def ingest_audio(self, path: str | Path) -> IngestionResult:
        self._emit("load", "Validating local audio")
        asset = self.media_loader.from_local(path)
        return self._ingest_media(asset)

    def ingest_youtube(self, url: str) -> IngestionResult:
        self._emit("download", "Downloading permitted YouTube audio")
        asset = self.media_loader.download_youtube(url)
        return self._ingest_media(asset)

    def _ingest_media(self, asset: MediaAsset) -> IngestionResult:
        self._emit("transcribe", f"Transcribing {asset.display_name}")
        transcription = self.transcriber.transcribe(asset)
        if not transcription.segments:
            raise IngestionError("transcription produced no segments")
        normalized_segments = tuple(
            TranscriptSegment(
                text=normalize_text(segment.text),
                start_seconds=segment.start_seconds,
                end_seconds=segment.end_seconds,
                page_number=segment.page_number,
            )
            for segment in transcription.segments
            if normalize_text(segment.text)
        )
        text = "\n\n".join(segment.text for segment in normalized_segments)
        source = SourceRef(
            source_id=asset.source_id,
            source_type=asset.source_type,
            original_uri=asset.original_uri,
            display_name=asset.display_name,
            checksum=asset.checksum,
        )
        document = IngestionDocument(
            source=source,
            title=asset.display_name,
            text=text,
            language=transcription.language,
            duration_seconds=transcription.duration_seconds,
            metadata=asset.metadata,
            segments=normalized_segments,
        )
        return self._finalize(document)

    def _finalize(self, document: IngestionDocument) -> IngestionResult:
        self._emit("chunk", "Creating deterministic chunks", 0.65)
        chunks = self.chunker.chunk(document)
        if not chunks:
            raise IngestionError("source produced no meaningful chunks")
        self._emit("stage", "Writing local staging records", 0.9)
        staging_dir = stage_records(
            self.output_root,
            document,
            chunks,
            environment=collect_environment_metadata(self.output_root),
        )
        self._emit("complete", "Ingestion complete", 1.0)
        return IngestionResult(document=document, chunks=chunks, staging_dir=staging_dir)

    def _emit(self, stage: str, message: str, fraction: float | None = None) -> None:
        if self.progress:
            self.progress(ProgressEvent(stage=stage, message=message, fraction=fraction))

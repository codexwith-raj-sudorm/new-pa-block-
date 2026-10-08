"""PDF extraction with page-level provenance."""

from __future__ import annotations

from pathlib import Path

from ..contracts import IngestionDocument, SourceRef, TranscriptSegment, stable_id
from ..errors import IngestionError, MissingDependencyError
from .normalize import normalize_text, normalize_title
from .utils import resolved_file, sha256_file


class PdfIngestor:
    """Extract selectable text from a PDF without uploading it anywhere."""

    def load(self, path: str | Path) -> IngestionDocument:
        source_path = resolved_file(path)
        if source_path.suffix.lower() != ".pdf":
            raise IngestionError(f"expected a .pdf file, got {source_path.name}")

        try:
            from pypdf import PdfReader
        except ImportError as exc:  # pragma: no cover - depends on environment
            raise MissingDependencyError(
                "PDF ingestion requires pypdf; install the base project dependencies"
            ) from exc

        try:
            reader = PdfReader(str(source_path))
            if reader.is_encrypted:
                raise IngestionError("encrypted PDFs are not supported in Phase 1")
            raw_pages = [page.extract_text() or "" for page in reader.pages]
        except IngestionError:
            raise
        except Exception as exc:  # pypdf exposes several parser-specific errors
            raise IngestionError(f"could not parse PDF {source_path.name}: {exc}") from exc

        pages: list[tuple[int, str]] = []
        for page_number, raw_text in enumerate(raw_pages, start=1):
            text = normalize_text(raw_text)
            if text:
                pages.append((page_number, text))
        if not pages:
            raise IngestionError(
                "PDF contains no selectable text; OCR is intentionally outside Phase 1"
            )

        segments = tuple(
            TranscriptSegment(text=text, page_number=page_number)
            for page_number, text in pages
        )
        document_text = "\n\n".join(text for _, text in pages)
        checksum = sha256_file(source_path)
        metadata = {"file_name": source_path.name, "absolute_path": str(source_path)}
        pdf_metadata = getattr(reader, "metadata", None) or {}
        for key, value in pdf_metadata.items():
            if value is not None:
                clean_key = str(key).lstrip("/").lower().replace(" ", "_")
                metadata[f"pdf_{clean_key}"] = str(value)

        title = normalize_title(
            str(pdf_metadata.get("/Title", "")) if pdf_metadata else "",
            fallback=source_path.stem,
        )
        source = SourceRef(
            source_id=stable_id("pdf", checksum),
            source_type="pdf",
            original_uri=str(source_path),
            display_name=source_path.name,
            checksum=checksum,
        )
        return IngestionDocument(
            source=source,
            title=title,
            text=document_text,
            page_count=len(raw_pages),
            metadata=metadata,
            segments=segments,
        )

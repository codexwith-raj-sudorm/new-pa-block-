from __future__ import annotations

from pathlib import Path

from study_assistant.contracts import MediaAsset, TranscriptSegment, TranscriptionResult
from study_assistant.ingestion.service import IngestionService


class FakeMediaLoader:
    def from_local(self, path: str | Path) -> MediaAsset:
        return MediaAsset(
            path=str(path),
            source_type="audio",
            original_uri=str(path),
            display_name="Fixture recording",
            source_id="fixture-audio",
            checksum="fixture-checksum",
        )


class FakeTranscriber:
    def transcribe(self, asset: MediaAsset) -> TranscriptionResult:
        return TranscriptionResult(
            segments=(
                TranscriptSegment("First timestamped idea", 0, 2),
                TranscriptSegment("Second timestamped idea", 2, 4),
            ),
            language="en",
            duration_seconds=4,
        )


def test_service_normalizes_transcribes_chunks_and_stages(tmp_path: Path) -> None:
    service = IngestionService(
        output_root=tmp_path,
        media_loader=FakeMediaLoader(),
        transcriber=FakeTranscriber(),
    )
    result = service.ingest_audio(tmp_path / "does-not-need-to-exist.wav")

    assert result.document.source.source_id == "fixture-audio"
    assert result.document.text.startswith("First timestamped idea")
    assert result.chunks
    assert result.chunks[0].start_seconds == 0
    assert result.chunks[-1].end_seconds == 4
    assert (result.staging_dir / "ingest.json").exists()
    assert (result.staging_dir / "chunks.jsonl").exists()

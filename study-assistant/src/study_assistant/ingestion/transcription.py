"""Lazy local Whisper transcription with a reusable model cache."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from threading import Lock
from typing import Any

from ..contracts import MediaAsset, TranscriptSegment, TranscriptionResult
from ..errors import IngestionError, MissingDependencyError


@dataclass(frozen=True)
class WhisperConfig:
    model_size: str = "tiny"
    device: str = "cpu"
    compute_type: str = "int8"
    download_root: str | None = None
    beam_size: int = 1
    vad_filter: bool = True

    def cache_key(self) -> tuple[Any, ...]:
        return (
            self.model_size,
            self.device,
            self.compute_type,
            self.download_root,
            self.beam_size,
            self.vad_filter,
        )


class WhisperTranscriber:
    """Transcribe media locally; model construction is deferred until first use."""

    _models: dict[tuple[Any, ...], Any] = {}
    _lock = Lock()

    def __init__(self, config: WhisperConfig | None = None) -> None:
        self.config = config or WhisperConfig()

    def _model(self) -> Any:
        key = self.config.cache_key()
        with self._lock:
            if key in self._models:
                return self._models[key]
            try:
                from faster_whisper import WhisperModel
            except ImportError as exc:  # pragma: no cover - depends on environment
                raise MissingDependencyError(
                    "transcription requires faster-whisper; install the transcription extra"
                ) from exc
            kwargs: dict[str, Any] = {
                "device": self.config.device,
                "compute_type": self.config.compute_type,
            }
            if self.config.download_root:
                kwargs["download_root"] = self.config.download_root
            try:
                model = WhisperModel(self.config.model_size, **kwargs)
            except Exception as exc:
                raise IngestionError(f"could not load Whisper model: {exc}") from exc
            self._models[key] = model
            return model

    def transcribe(self, asset: MediaAsset) -> TranscriptionResult:
        media_path = Path(asset.path)
        if not media_path.exists():
            raise IngestionError(f"media file does not exist: {media_path}")
        model = self._model()
        try:
            segments, info = model.transcribe(
                str(media_path),
                beam_size=self.config.beam_size,
                vad_filter=self.config.vad_filter,
            )
            # faster-whisper returns a lazy generator; consume it while the
            # model/runtime is still valid and convert to our stable contract.
            result_segments = tuple(
                TranscriptSegment(
                    text=str(segment.text).strip(),
                    start_seconds=float(segment.start),
                    end_seconds=float(segment.end),
                )
                for segment in segments
                if str(segment.text).strip()
            )
        except Exception as exc:
            raise IngestionError(f"transcription failed for {media_path.name}: {exc}") from exc

        if not result_segments:
            raise IngestionError("transcription produced no meaningful speech segments")
        language = getattr(info, "language", None)
        duration = getattr(info, "duration", None)
        return TranscriptionResult(
            segments=result_segments,
            language=str(language) if language else None,
            duration_seconds=float(duration) if duration is not None else asset.duration_seconds,
        )

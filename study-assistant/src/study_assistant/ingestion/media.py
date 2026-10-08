"""Local audio validation and single-video YouTube download interface."""

from __future__ import annotations

import os
from pathlib import Path
from urllib.parse import urlsplit

from ..contracts import MediaAsset, stable_id
from ..errors import IngestionError, MissingDependencyError
from .utils import canonical_url, resolved_file, safe_name, sha256_file

AUDIO_SUFFIXES = {
    ".aac",
    ".flac",
    ".m4a",
    ".mp3",
    ".ogg",
    ".opus",
    ".wav",
    ".webm",
}
YOUTUBE_HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"}


def validate_audio_file(path: str | os.PathLike[str]) -> Path:
    audio_path = resolved_file(path)
    if audio_path.suffix.lower() not in AUDIO_SUFFIXES:
        supported = ", ".join(sorted(AUDIO_SUFFIXES))
        raise IngestionError(
            f"unsupported audio extension {audio_path.suffix!r}; expected one of {supported}"
        )
    return audio_path


class MediaLoader:
    """Create stable media assets in a caller-owned local staging directory."""

    def __init__(self, staging_root: str | os.PathLike[str] = "var/staging") -> None:
        self.staging_root = Path(staging_root).expanduser()

    def from_local(self, path: str | os.PathLike[str]) -> MediaAsset:
        audio_path = validate_audio_file(path)
        checksum = sha256_file(audio_path)
        return MediaAsset(
            path=str(audio_path),
            source_type="audio",
            original_uri=str(audio_path),
            display_name=audio_path.name,
            source_id=stable_id("audio", checksum),
            checksum=checksum,
            metadata={"file_name": audio_path.name, "absolute_path": str(audio_path)},
        )

    def download_youtube(self, url: str) -> MediaAsset:
        normalized_url = canonical_url(url)
        host = urlsplit(normalized_url).netloc
        if host not in YOUTUBE_HOSTS:
            raise IngestionError("only youtube.com and youtu.be URLs are supported")

        try:
            import yt_dlp
        except ImportError as exc:  # pragma: no cover - depends on environment
            raise MissingDependencyError(
                "YouTube ingestion requires yt-dlp; install the media extra"
            ) from exc

        source_id = stable_id("youtube", normalized_url)
        job_dir = self.staging_root / source_id / "media"
        job_dir.mkdir(parents=True, exist_ok=True)
        output_template = str(job_dir / "source.%(ext)s")
        options = {
            "format": "bestaudio/best",
            "noplaylist": True,
            "outtmpl": output_template,
            "quiet": True,
            "no_warnings": True,
            "postprocessors": [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "96",
                }
            ],
        }
        try:
            with yt_dlp.YoutubeDL(options) as downloader:
                info = downloader.extract_info(normalized_url, download=True)
        except Exception as exc:
            raise IngestionError(f"YouTube download failed: {exc}") from exc

        candidates = sorted(
            path
            for path in job_dir.iterdir()
            if path.is_file() and path.suffix.lower() in AUDIO_SUFFIXES
        )
        if not candidates:
            raise IngestionError("YouTube download completed without a usable audio file")
        audio_path = candidates[0]
        checksum = sha256_file(audio_path)
        title = str(info.get("title") or safe_name(normalized_url))
        duration = info.get("duration")
        return MediaAsset(
            path=str(audio_path),
            source_type="youtube",
            original_uri=normalized_url,
            display_name=title,
            source_id=source_id,
            checksum=checksum,
            duration_seconds=float(duration) if duration is not None else None,
            metadata={
                "video_id": str(info.get("id") or ""),
                "uploader": str(info.get("uploader") or ""),
            },
        )

# Local Study Assistant

This is the incremental implementation of the free-first study assistant described in the repository's [`mind.md`](../mind.md). The project keeps study material local by default and adds one phase at a time.

## Phase 1 status

Phase 1 provides:

- PDF extraction with page-level provenance;
- local audio and permitted YouTube audio ingestion interfaces;
- lazy, offline `faster-whisper` transcription with timestamped segments;
- conservative text normalization;
- deterministic recursive chunks with page/timestamp provenance;
- JSON/JSONL local staging, including compact environment metadata; and
- typed errors and progress events suitable for a later UI.

It deliberately does **not** embed, retrieve, call an LLM, upload files, or write to a hosted service.

## Setup

Python 3.10 or newer is required. From this directory:

```bash
python -m venv .venv
. .venv/bin/activate                 # Windows: .venv\\Scripts\\activate
python -m pip install --upgrade pip
python -m pip install -e ".[all,dev]"
```

The `pypdf` and chunking dependencies are lightweight. `faster-whisper` downloads a model only when transcription is first used. `yt-dlp` requires FFmpeg for reliable audio extraction; install FFmpeg separately and confirm both `ffmpeg` and `ffprobe` are on `PATH`.

For the smallest PDF/chunk-only environment, install `-e ".[dev]"`; media and transcription providers are imported lazily and are only required for those commands.

## Phase 1 commands

Run the tests without downloading a model:

```bash
python -m pytest
```

Inspect Python, platform, FFmpeg, and available-disk metadata before a job:

```bash
python -m study_assistant.cli doctor --output-root var/staging
```

Ingest a PDF:

```bash
python -m study_assistant.cli pdf path/to/notes.pdf --output-root var/staging
```

Ingest a local audio file with the default `tiny` CPU/int8 Whisper configuration:

```bash
python -m study_assistant.cli audio path/to/lecture.m4a --output-root var/staging
```

Ingest a permitted YouTube lecture URL (single video only):

```bash
python -m study_assistant.cli youtube 'https://www.youtube.com/watch?v=VIDEO_ID' --output-root var/staging
```

The command prints the source ID and staging directory. Staged records are human-readable `ingest.json` and newline-delimited `chunks.jsonl` under `var/staging/<source_id>/`. `var/`, virtual environments, caches, model files, and temporary media are ignored by Git.

## Locality and permissions

The YouTube loader is an interface for URLs the user is permitted to process. It rejects unsupported URL shapes, disables playlists, and never bypasses DRM or paywalls. No network operation is performed during PDF or local-audio ingestion. Whisper model downloads are the only expected network activity after setup, and they can be pre-cached before working offline.

## Design notes

- Stable source IDs are derived from a normalized URL or file checksum.
- Chunk IDs include the source ID, chunk ordinal, text fingerprint, and chunk configuration.
- Page numbers and transcript timestamps are retained on every chunk where available.
- The model is loaded lazily and cached per configuration rather than once per file.
- The default staging format is plain JSON so it remains inspectable and can become a later SQLite import boundary.

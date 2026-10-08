# Local Study Assistant

This is the incremental implementation of the free-first study assistant described in the repository's [`mind.md`](../mind.md). It now includes the Phase 1–6 local MVP: ingestion, local embeddings, hybrid retrieval, grounded answers, cited study artifacts, SQLite review state, browser speech, and a Streamlit interface.

## What is free and local

- PDF/audio/YouTube ingestion: `pypdf`, `yt-dlp`, and optional `faster-whisper`.
- Embeddings: local Sentence Transformers, CPU by default.
- Search: SQLite float32 vectors plus SQLite FTS5 exact-term search.
- Answers: transparent extractive evidence by default, optional local Ollama only.
- Notes, flashcards, quizzes, reviews: local SQLite.
- Speech: browser SpeechSynthesis; no cloud TTS.
- UI: optional Streamlit.

No paid API key, hosted vector database, AWS, Redis, hosted queue, cloud TTS, or cloud LLM is required.

## Setup

Python 3.10 or newer is required. From this directory:

```bash
python -m venv .venv
. .venv/bin/activate                 # Windows: .venv\\Scripts\\activate
python -m pip install --upgrade pip
python -m pip install -e ".[all,dev]"
```

For a lightweight test environment without downloading application models:

```bash
python -m pip install -e ".[dev]"
python -m pytest
```

The real Sentence Transformer model is downloaded only when the first `index`, `search`, or UI operation needs it. Keep the model cache outside Git. FFmpeg is required for reliable audio/YouTube processing; verify `ffmpeg -version` and `ffprobe -version`.

## Separate Python build

The study assistant is an independent Python package under `study-assistant/`; it is separate from the Android project. Build a wheel and source archive locally without committing generated artifacts:

```bash
cd study-assistant
python -m pip install -e ".[dev]"
./build.sh
```

The generated files are written to `study-assistant/dist/` and are ignored by Git. The same test-and-build process runs in `.github/workflows/study-assistant.yml` and uploads the wheel/source archive as a CI artifact.

Install a locally built wheel into another virtual environment with:

```bash
python -m pip install dist/local_study_assistant-*.whl
```

## Quickest testable path

### 0. Run the fully offline demo first

This exercises indexing, hybrid retrieval, extractive grounded output, cited artifacts, quiz persistence, and review storage without a PDF, network, or downloaded embedding model:

```bash
study-assistant demo --root var/demo
```

### 1. Check the machine

```bash
study-assistant doctor --output-root var/staging
```

### 2. Ingest a PDF

```bash
study-assistant pdf path/to/notes.pdf --output-root var/staging
```

The command prints a `source_id` and a staging directory such as `var/staging/SOURCE_ID`.

### 3. Install the local embedding provider and index

```bash
python -m pip install -e ".[embeddings]"
study-assistant index var/staging/SOURCE_ID \
  --database var/index/metadata.sqlite
```

The first index run downloads `all-MiniLM-L6-v2` from the model registry. After it is cached, indexing/search can run offline.

### 4. Search or ask without an LLM

```bash
study-assistant search "the main definition" \
  --database var/index/metadata.sqlite --top-k 5

study-assistant ask "What is the main definition?" \
  --database var/index/metadata.sqlite
```

Default `ask` mode is deliberately extractive: it displays retrieved evidence and citations instead of inventing a synthesized answer.

### 5. Generate local study artifacts

```bash
study-assistant notes "the main definition" \
  --database var/index/metadata.sqlite --study-database var/study.sqlite

study-assistant flashcards "the main definition" \
  --database var/index/metadata.sqlite --study-database var/study.sqlite

study-assistant quiz "the main definition" \
  --database var/index/metadata.sqlite --study-database var/study.sqlite
```

These commands use deterministic extractive generators, validate citations, and persist results locally.

### 6. Review and export

```bash
study-assistant sources --study-database var/study.sqlite
study-assistant review CARD_ID good --study-database var/study.sqlite
study-assistant export var/study-export.json --study-database var/study.sqlite
study-assistant export var/study-backup.sqlite --study-database var/study.sqlite
```

Review grades are `again`, `hard`, `good`, and `easy`.

## Optional local Ollama answers

Install Ollama separately, pull a local model, and leave the study assistant pointed at the local default endpoint:

```bash
ollama pull llama3.2:3b
study-assistant ask "Explain this concept" \
  --database var/index/metadata.sqlite --ollama --ollama-model llama3.2:3b
```

If Ollama is unavailable, the CLI falls back to cited evidence. No cloud fallback occurs.

## Streamlit UI

Install the UI extra, then start the local app:

```bash
python -m pip install -e ".[all,dev]"
study-assistant-ui
# or: streamlit run src/study_assistant/ui/app.py
```

The UI has Library, Ingest, Tutor, Notes, Flashcards, and Quiz tabs. Set `STUDY_ASSISTANT_HOME` to choose a local data directory. Browser read-aloud uses the browser's built-in SpeechSynthesis API.

## Supported inputs and locality

- PDFs with selectable text are supported; OCR is outside the current MVP.
- Local audio accepts WAV, MP3, M4A, FLAC, OGG, OPUS, and WEBM.
- YouTube accepts one explicit `youtube.com` or `youtu.be` URL at a time. The loader disables playlists and does not bypass DRM, paywalls, or access controls. Process only material you are permitted to use.
- Generated state is stored under `var/`, which is ignored by Git.
- Model files, virtual environments, staging data, databases, and caches are not committed.

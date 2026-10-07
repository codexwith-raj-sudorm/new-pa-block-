# Study Assistant — Structured Build Plan

**Status:** Planning is complete at the system-design level. Implementation is beginning with Phase 1 only; later phases remain design-only until their preceding acceptance criteria pass.

## Design principles

1. **Free-first:** local processing, SQLite, local embeddings, optional local Ollama, and browser/system TTS before any paid provider.
2. **Space-efficient:** lazy-load heavy models, reuse model instances, batch work, cache by fingerprint, and keep generated artifacts out of Git.
3. **Fast feedback:** build a vertical slice early, expose structured progress, and use deterministic fixtures for tests.
4. **Grounded output:** every generated answer or study artifact must cite the staged source chunks that support it.
5. **Local privacy:** study files, transcripts, embeddings, questions, and review history stay local in the baseline configuration.
6. **Replaceable providers:** ingestion, embeddings, generation, storage, and TTS use small interfaces so optional providers can be added without rewriting the core.
7. **No premature infrastructure:** no hosted vector database, cloud queue, Redis, Kubernetes, or paid API is required for the MVP.

## System layout

```text
study-assistant/
  pyproject.toml                 # dependencies and tooling
  README.md                      # setup and operation
  mind.md                        # this plan
  src/study_assistant/
    contracts.py                 # validated cross-phase data records
    config.py                    # limits, paths, and provider settings
    errors.py                    # typed user-safe errors
    ingestion/                   # Phase 1
    embeddings/                  # Phase 2
    retrieval/                   # Phase 3
    generation/                  # Phase 4
    persistence/                 # Phase 5
    audio/                       # Phase 5 local TTS
    ui/                          # Phase 6 Streamlit interface
  tests/                         # unit, fixture, determinism, and E2E tests
  var/                            # ignored local cache, staging, and database
```

## Phase map

| Phase | Deliverable | Free-first default | Gate |
|---|---|---|---|
| 1 | Validated text/chunk staging | pypdf, yt-dlp, faster-whisper, SQLite/files | deterministic chunks with provenance |
| 2 | Local semantic index | Sentence Transformers + NumPy/SQLite | repeatable vector search |
| 3 | Grounded tutor | SQLite FTS5 + cosine search + optional Ollama | cited answers or explicit missing evidence |
| 4 | Study artifacts | local provider + Pydantic validation + extractive fallback | valid cited notes/cards/quizzes |
| 5 | Durable study state | SQLite + local/browser TTS + SM-2-style review | restart-safe history and scheduling |
| 6 | Local user interface | Streamlit + structured background jobs | complete fixture path works offline after caching |

## Current implementation status

- **Phase 1 planning:** complete.
- **Phase 1 implementation:** starting now.
- **Phases 2–6 planning:** complete in the sections below.
- **Application code:** do not begin Phase 2 until Phase 1 tests and acceptance criteria pass.

Phase 1: Environment Initialization, Ingestion & Deterministic Chunking
===============================================================

### Phase 1 objective

Build a reliable local ingestion boundary that accepts PDFs, local audio recordings, and permitted YouTube lecture URLs; converts each source into clean, timestamp-aware text; attaches stable provenance metadata; and emits deterministic chunks ready for Phase 2 embedding.

Phase 1 must not call an LLM, create embeddings, write to a vector database, or silently upload study material. Its output is a validated `IngestionDocument` plus deterministic `TextChunk` records stored in a local staging area.

### 1. Scope and non-goals

**In scope**

- Reproducible Python environment creation and dependency pinning.
- PDF text extraction with page-level provenance.
- YouTube audio extraction through `yt-dlp` for URLs the user is permitted to process.
- Local audio-file ingestion.
- Offline transcription with `faster-whisper`.
- Timestamp-preserving transcript segments.
- Text normalization without destroying source meaning.
- Deterministic recursive chunking with overlap.
- Validation, structured error reporting, retries, caching, and ingestion logs.
- Unit and fixture tests for each supported source type.

**Not in scope**

- Embedding generation or vector-store writes.
- RAG retrieval, prompting, summarization, quizzes, or flashcards.
- Automatic downloading from arbitrary websites.
- DRM circumvention, paywall bypassing, or processing content without permission.
- Cloud transcription or cloud storage.
- Deleting the user's original files.

### 2. Reproducible environment

Use a modular Python project with a supported Python version recorded in project metadata. Create an isolated virtual environment before installing dependencies:

```bash
python3 -m venv .venv
source .venv/bin/activate              # Windows: .venv\\Scripts\\activate
python -m pip install --upgrade pip
pip install langchain-text-splitters langchain-community pypdf yt-dlp faster-whisper
```

The final project should replace an unpinned install command with a committed dependency file such as `requirements.txt` or `pyproject.toml` containing tested version ranges or exact versions. Record the Python version, operating-system assumptions, and CPU/GPU support in the setup documentation.

`faster-whisper` depends on native runtime components. Confirm the selected `ctranslate2` version supports the chosen Python version and CPU architecture. Install FFmpeg separately and verify it is available on `PATH`:

```bash
ffmpeg -version
ffprobe -version
```

FFmpeg is required for robust audio extraction, codec conversion, duration checks, and handling containers that do not expose a simple MP3 stream. The application should report a clear setup error when FFmpeg is unavailable instead of failing with a generic subprocess exception.

Recommended environment checks:

- Verify Python is at the supported version.
- Verify every required import before starting the application.
- Verify `yt-dlp --version`, `ffmpeg -version`, and `ffprobe -version`.
- Confirm the process has read access to input files and write access to the staging/cache directory.
- Confirm enough disk space exists for the source, temporary media, transcript, and model cache.
- Record the Whisper model name, compute type, and runtime device in a diagnostic report.

### 3. Phase 1 data contracts

Define explicit contracts before implementing individual loaders. These contracts prevent later phases from depending on loader-specific return values.

```python
@dataclass
class SourceRef:
    source_id: str                 # stable hash or generated UUID
    source_type: Literal["pdf", "audio", "youtube"]
    original_uri: str              # local path or permitted URL
    display_name: str
    checksum: str | None

@dataclass
class IngestionDocument:
    source: SourceRef
    title: str
    text: str
    language: str | None
    duration_seconds: float | None
    page_count: int | None
    metadata: dict[str, str]
    segments: list["TranscriptSegment"]

@dataclass
class TranscriptSegment:
    text: str
    start_seconds: float | None
    end_seconds: float | None
    page_number: int | None

@dataclass
class TextChunk:
    chunk_id: str
    source_id: str
    text: str
    ordinal: int
    start_seconds: float | None
    end_seconds: float | None
    page_start: int | None
    page_end: int | None
    metadata: dict[str, str]
```

The exact implementation may use Pydantic models instead of dataclasses, but validation must reject empty source identifiers, invalid timestamps, unsupported source types, and chunks that contain no meaningful text.

### 4. Source identity, paths, and cache layout

Use a per-source staging directory rather than writing every YouTube download to `lecture.mp3`. A fixed filename causes collisions when two jobs run concurrently and can accidentally reuse the wrong recording.

Suggested layout:

```text
var/
  sources/<source_id>/
    original/
    media/
    pages/
    transcript.json
    chunks.jsonl
    ingest.json
    errors.jsonl
  cache/
    whisper/<model-name>/
    downloads/
```

Generate `source_id` from a stable source fingerprint, such as a normalized local path plus file checksum or a normalized URL plus extractor identifier. Keep the original URL separately for provenance. Use safe filenames derived from the source title, never raw URL text.

Required cache behavior:

- A completed source can be resumed without retranscribing it.
- A partial download is stored under a temporary name and atomically renamed only after validation.
- A model download is shared by compatible jobs and never deleted while in use.
- Cache entries record tool versions, model name, language, and options so incompatible results are not silently reused.
- Temporary files are cleaned after success or retained with an explicit failed-job marker for debugging.

### 5. YouTube and local-audio ingestion

Create one media-ingestion interface with two adapters:

```text
MediaSourceAdapter
  ├── YouTubeAdapter
  └── LocalAudioAdapter
```

Both adapters should output a validated local audio path, duration, media metadata, and a cleanup policy. The transcription layer should not know whether audio came from a URL or a local file.

#### YouTube adapter

The initial `yt-dlp` configuration should be more defensive than a fixed `lecture.%(ext)s` output:

- Validate that the input is an allowed HTTP(S) URL.
- Use a per-source output directory and a unique temporary filename.
- Request the best available audio-only format within configured size/duration limits.
- Prefer a stable conversion format such as WAV or a supported compressed format after extraction.
- Use FFmpeg post-processing only after download completion.
- Capture title, channel/uploader, webpage URL, upload date when available, duration, and extractor metadata.
- Keep a download progress callback for UI/logging.
- Fail clearly for private, unavailable, geo-restricted, age-restricted, or unsupported media.
- Respect the platform's terms, copyright, and the user's authorization to process the lecture.
- Never accept a URL as a shell command or concatenate it into a shell string.

Do not assume every source can be converted to MP3. Let FFmpeg choose a compatible intermediate format, then normalize audio for Whisper if necessary. Validate the resulting file with `ffprobe` before transcription.

#### Local audio adapter

Accept an explicit allowlist of audio extensions and inspect the actual media stream with FFprobe. Validate:

- The path is a regular file inside an allowed input directory.
- The file size is within configured limits.
- The file contains an audio stream.
- The duration is nonzero and within the maximum job duration.
- The file is not a symlink escaping the allowed directory when sandboxing is enabled.

Copy or reference the input according to a configurable retention policy; never mutate the user's original recording.

### 6. Whisper transcription pipeline

Use a process-wide or job-worker model cache rather than constructing `WhisperModel("small", ...)` for every lecture. Model initialization is expensive and repeated initialization can exhaust memory when jobs overlap.

Recommended configuration object:

```python
WhisperConfig(
    model_name="small",
    device="cpu",
    compute_type="int8",
    beam_size=5,
    language=None,
    vad_filter=True,
)
```

Allow the model name, device, compute type, beam size, language, and VAD behavior to be configured without changing ingestion code. Start with CPU `int8` as the baseline, then add an explicitly detected CUDA path later.

Call transcription in a way that preserves the generator's segment metadata. `faster-whisper` returns a lazy segment iterator; consume it once and write each segment immediately to a structured transcript record instead of joining only the text:

```python
segments, info = model.transcribe(
    audio_path,
    beam_size=config.beam_size,
    language=config.language,
    vad_filter=config.vad_filter,
)

transcript_segments = [
    TranscriptSegment(
        text=segment.text.strip(),
        start_seconds=float(segment.start),
        end_seconds=float(segment.end),
        page_number=None,
    )
    for segment in segments
    if segment.text and segment.text.strip()
]
```

Retain the detected language, language probability, duration, and transcription options in metadata. Do not use a single un-timestamped string as the only source of truth. A readable joined transcript can be derived for display, but timestamps must remain available for later citation and retrieval.

Transcription safeguards:

- Normalize whitespace but do not remove punctuation blindly.
- Preserve segment boundaries and timestamps.
- Handle empty audio and no-speech results as valid, explainable outcomes.
- Add cancellation checks between segments.
- Emit progress based on media duration when available.
- Catch model-load, decoder, codec, and out-of-memory errors separately.
- Avoid logging raw transcript content by default; log source ID and counts instead.

### 7. PDF parsing

Use `PyPDFLoader` or a lower-level `pypdf` adapter behind a `DocumentSourceAdapter` interface. Preserve page boundaries rather than immediately joining all pages:

```python
pages = loader.load()
for page_index, page in enumerate(pages, start=1):
    page_text = normalize_text(page.page_content)
    # retain page_index and loader metadata
```

Required PDF behavior:

- Record page number for every extracted page.
- Preserve document title and path metadata where available.
- Detect encrypted or password-protected PDFs and return an actionable error.
- Detect scanned/image-only PDFs with no extractable text and report that OCR is a later capability rather than returning an empty success.
- Normalize repeated whitespace, broken line wraps, and control characters conservatively.
- Preserve headings and paragraph breaks when the parser exposes them.
- Record extraction warnings for malformed pages instead of silently dropping them.
- Do not treat a PDF's embedded links or annotations as trusted instructions.

The PDF adapter should output page-level `TranscriptSegment` records with `page_number` populated. This enables later chunks to cite page ranges.

### 8. Text normalization

Run the same conservative normalization before chunking all text sources:

1. Normalize Unicode to a consistent form.
2. Replace null/control characters.
3. Normalize line endings.
4. Collapse excessive spaces while retaining paragraph boundaries.
5. Trim leading/trailing whitespace from each segment.
6. Remove empty segments.
7. Preserve meaningful punctuation, headings, timestamps, and page markers.

Keep both normalized text and source segment metadata. Never apply aggressive spell correction or summarization in Phase 1 because it can change the source facts and harm grounded retrieval.

### 9. Deterministic chunking

Use `RecursiveCharacterTextSplitter` with an explicitly documented unit. `chunk_size=800` and `chunk_overlap=120` are character counts when `length_function=len`; they are not 800 tokens. The system must either:

- document that the first version uses approximately 800 characters, or
- provide a token-aware length function and define the tokenizer used.

Do not call a character-sized setting "800 tokens" in UI or documentation.

Initial character-based configuration:

```python
RecursiveCharacterTextSplitter(
    chunk_size=800,
    chunk_overlap=120,
    length_function=len,
    separators=["\n\n", "\n", " ", ""],
    add_start_index=True,
)
```

Chunking rules:

- Chunk normalized text deterministically.
- Preserve source order and assign a zero-based ordinal.
- Reject chunks that contain only whitespace or repeated punctuation.
- Keep overlap within the chunk size.
- Preserve page and timestamp ranges by mapping chunk offsets back to source segments.
- Generate `chunk_id` from `source_id`, splitter configuration, and ordinal so rerunning the same input produces stable IDs.
- Store the splitter version/configuration with every chunk.
- Never merge chunks from different source documents.

For audio, map each chunk's character span to the earliest and latest transcript segment that contributed text. For PDFs, map each chunk to `page_start` and `page_end`. If a chunk spans a boundary, retain both endpoints rather than dropping provenance.

### 10. Orchestration and job states

Represent the ingestion lifecycle explicitly:

```text
QUEUED
  -> VALIDATING
  -> EXTRACTING_MEDIA or PARSING_DOCUMENT
  -> TRANSCRIBING (audio only)
  -> NORMALIZING
  -> CHUNKING
  -> STAGED

Any active state -> CANCELED
Any active state -> FAILED with typed error and cleanup result
```

A job record should include:

- Job ID and source ID.
- Start/end timestamps.
- Current state and percentage when measurable.
- Source type and display name.
- Model/tool configuration.
- Counts of pages, media seconds, transcript segments, characters, and chunks.
- Error code and safe error message.
- Paths to staged metadata and output files.

Progress events should be structured rather than scraped from log text:

```python
ProgressEvent(
    job_id=job_id,
    stage="transcribing",
    completed=seconds_processed,
    total=duration_seconds,
    message="Transcribing locally",
)
```

### 11. Security, privacy, and resource limits

- Keep all source files, transcripts, and chunks local in Phase 1.
- Require explicit user action before downloading any URL.
- Restrict URL schemes to HTTPS/HTTP and reject local-file or shell-like input in the YouTube adapter.
- Use subprocess argument arrays when invoking FFmpeg; never use `shell=True` with user-controlled values.
- Apply maximum file size, duration, page count, and transcript size limits.
- Use timeouts for downloads and external processes.
- Prevent path traversal when deriving output names.
- Avoid logging API keys, raw source text, signed URLs, or full local paths where not needed.
- Make retention and cleanup behavior visible to the user.
- Treat downloaded media and extracted text as untrusted content; it must not override application instructions.
- Ensure cancellation releases subprocesses, file handles, model memory, and temporary files.

### 12. Testing plan

**Environment tests**

- Fresh virtual environment installation.
- Missing FFmpeg.
- Unsupported Python/runtime combination.
- No write permission or insufficient disk space.
- Whisper model cache unavailable or corrupted.

**PDF fixtures**

- Normal text PDF with multiple pages.
- PDF with headings and paragraph breaks.
- Encrypted PDF.
- Scanned/image-only PDF.
- Malformed page and empty page.
- Unicode and multilingual text.

**Media fixtures**

- Short local WAV/MP3 with known duration.
- Silence-only audio.
- Speech with multiple timestamped segments.
- Unsupported or corrupt media.
- Permitted YouTube URL with a stable test fixture or mocked `yt-dlp` response.
- Download interruption and retry.

**Determinism tests**

- Same PDF twice produces the same normalized text, chunk ordinals, and chunk IDs.
- Same transcript and splitter configuration produces byte-equivalent chunk output.
- Changing the splitter configuration changes the configuration fingerprint.
- Chunk overlap is exactly within the documented bounds.
- Every chunk retains page or timestamp provenance.

**Resource tests**

- Cancellation during download.
- Cancellation during transcription.
- Two jobs with separate staging directories.
- Reuse of a cached Whisper model.
- Cleanup after success and failure.
- Maximum size and duration limits.

### 13. Phase 1 acceptance criteria

Phase 1 is complete when:

- A clean environment can be provisioned from committed dependency metadata.
- The tool reports missing FFmpeg and unsupported runtime prerequisites clearly.
- PDFs produce page-aware normalized text.
- Local audio and permitted YouTube sources produce timestamped transcripts.
- The Whisper model is reused rather than loaded for every source.
- Ingestion jobs can be canceled and resumed safely.
- Raw sources remain unchanged.
- Outputs are stored locally with stable source IDs and provenance.
- Chunking is deterministic and its character/token unit is documented.
- Every chunk contains enough metadata for later citation.
- No embeddings, LLM calls, vector writes, or cloud uploads occur.
- Unit, fixture, failure-path, and determinism tests pass.

Phase 2: Local Embeddings & Cost-Free Semantic Indexing
========================================================

### Objective

Turn Phase 1 `TextChunk` records into locally generated, normalized vectors and store them in a local index without Pinecone, paid API calls, or a hosted database.

### Free-first technical decision

Use a provider interface with this default path:

- Embedding model: a small local Sentence Transformers model such as `all-MiniLM-L6-v2` or another approved 384-dimensional model.
- Runtime: CPU by default; optional local GPU when available.
- Storage: SQLite for metadata and NumPy memory-mapped arrays or SQLite BLOBs for vectors.
- Search: exact cosine similarity for the first MVP. Add SQLite FTS5 lexical search in Phase 3 for hybrid retrieval.
- Optional upgrade: ChromaDB behind the same repository interface. Pinecone is not part of the free baseline.

This is fast to implement and has no per-query cost. The trade-off is that brute-force search is intended for a personal library or early prototype. Add an ANN index only after measuring the collection size and latency.

### Embedding contracts

```python
class EmbeddingProvider(Protocol):
    model_name: str
    dimension: int
    def embed_documents(self, texts: list[str]) -> list[list[float]]: ...
    def embed_query(self, text: str) -> list[float]: ...

@dataclass
class VectorRecord:
    chunk_id: str
    source_id: str
    model_name: str
    dimension: int
    vector: bytes
    norm: float
    created_at: str
```

The provider must use exactly the same model and preprocessing for document and query embeddings. Every vector record stores the model name, dimension, normalization state, and a configuration fingerprint. Never compare vectors produced by different models or dimensions.

### Indexing flow

```text
staged chunks
  -> validate text and metadata
  -> batch embedding provider
  -> L2-normalize vectors
  -> write metadata + vector atomically
  -> update source/index status
  -> verify counts and dimensions
```

Indexing requirements:

- Batch texts to limit memory use.
- Preserve `chunk_id`, source, page/timestamp provenance, ordinal, and text hash.
- Make upserts idempotent; rerunning the same source must update rather than duplicate records.
- Use a transaction for each batch or source.
- Mark an index build as `building`, `ready`, or `failed`.
- Do not mark a source ready until every chunk has a valid vector.
- Support deleting all vectors for one source without touching other sources.
- Support a full rebuild when the model or chunking configuration changes.
- Store model/cache paths outside Git and never commit model binaries or user material.

### Local storage layout

```text
var/index/
  metadata.sqlite
  vectors/<embedding-model>/<source-id>.npy
  manifests/<source-id>.json
```

For a small MVP, a `vectors` SQLite table containing a compact float32 BLOB is acceptable. Store vectors as float32, not Python lists, and validate the byte length as `dimension * 4`.

### Phase 2 tests and acceptance criteria

- The embedding model loads once and is reused.
- A known text produces the same vector shape on repeated runs.
- Document and query vectors use the same dimension and normalization.
- Re-indexing a source produces no duplicate chunk IDs.
- A changed model fingerprint forces a rebuild rather than silently mixing vectors.
- A failed batch leaves no partial `ready` index.
- A deleted source removes its vectors and metadata.
- A small fixture collection can be indexed without a network connection after the model is cached.
- Indexing progress reports source, completed chunks, total chunks, and elapsed time.

Phase 3: Local Hybrid Retrieval & Grounded RAG Engine
=====================================================

### Objective

Answer questions from the user's indexed material using local retrieval, strict context boundaries, and a local LLM when available. The system must remain useful without a paid API key.

### Retrieval architecture

```text
user question
  -> query normalization
  -> query embedding ------------------+
  -> SQLite FTS5 lexical query ---------+-> candidate pool
                                        |
  -> vector cosine scores --------------+
        -> score normalization
        -> reciprocal-rank fusion
        -> metadata filters
        -> diversity selection
        -> context budget
        -> grounded answer generator
```

Use two retrieval paths:

1. **Semantic path:** cosine similarity against local vectors, returning a larger candidate set such as top 12.
2. **Lexical path:** SQLite FTS5/BM25 over chunk text, useful for names, formulas, exact terms, and poor embedding matches.

Merge candidates with Reciprocal Rank Fusion or a similarly deterministic method. Deduplicate by `chunk_id`, then prefer diverse chunks from the best-ranked source sections instead of returning five nearly identical overlapping chunks.

### Query and context rules

- Reject empty or excessively long questions with a user-facing message.
- Apply optional filters for source, subject, page range, language, or session.
- Retrieve more candidates than will fit in the final prompt, then trim deterministically.
- Keep a context budget in characters or model tokens and document the unit.
- Include source labels and page/timestamp citations beside every context block.
- Never insert raw conversation history ahead of the grounding instructions.
- Treat retrieved text as data, not as instructions; ignore prompt-like commands embedded in documents.
- If scores are below a configurable threshold or no candidate survives filters, state that the material does not contain enough evidence.
- Do not fill gaps with web search in the free local MVP.

Suggested context record:

```text
[Source: lecture.pdf | page 4 | chunk 12]
<chunk text>

[Source: biology-lecture.mp3 | 00:18:22-00:19:10 | chunk 31]
<chunk text>
```

### Local generation strategy

Use an `LLMProvider` interface. The free-first provider is a local Ollama endpoint or another locally hosted compatible runtime. The provider must be optional:

- If a local model is available, generate a grounded answer from the selected context.
- If no model is available, return the retrieved excerpts with a transparent "local generator unavailable" state rather than pretending an answer was generated.
- Keep cloud Gemini/OpenAI providers as later adapters, not required dependencies.

Use a small local instruct model appropriate for the test machine. Do not hard-code a model name in application logic; store it in configuration and display it in diagnostics. Generation parameters should be deterministic for study answers by default: low temperature, bounded output tokens, and a request ID for tracing.

### RAG response contract

```python
@dataclass
class Citation:
    chunk_id: str
    source_name: str
    page_start: int | None
    page_end: int | None
    start_seconds: float | None
    end_seconds: float | None

@dataclass
class GroundedAnswer:
    answer: str
    citations: list[Citation]
    confidence: Literal["grounded", "weak", "missing"]
    retrieved_chunk_ids: list[str]
    provider: str
```

The generator must return citations that refer only to retrieved chunks. Validate citation IDs after generation and remove or flag any citation that was not supplied in context.

### Phase 3 acceptance criteria

- A local indexed fixture can answer an exact fact with a page citation.
- Lexical search finds a rare term that semantic search misses.
- Low-score questions produce a clear insufficient-evidence response.
- Retrieved context stays within the configured budget.
- A document containing prompt-injection text cannot override system grounding rules.
- The no-LLM mode still exposes retrieved evidence without fabricating an answer.
- The same query and fixture index produce stable retrieval order.

Phase 4: Structured Notes, Flashcards & Quiz Generators
=======================================================

### Objective

Generate useful study artifacts from grounded chunks using validated schemas and a local-first generation path. Every generated item must retain source citations and must be rejected or repaired when its structure is invalid.

### Shared generation pipeline

```text
selected document/chunks
  -> task-specific instruction
  -> bounded context assembly
  -> local LLM provider
  -> JSON/Markdown candidate
  -> schema validation
  -> citation validation
  -> deterministic repair or retry
  -> SQLite persistence
  -> UI presentation
```

Do not generate from an entire document without retrieval or context limits. Large documents should be summarized section by section, then optionally combined in a second local pass.

### Notes schema

Notes should be Markdown for easy export, but store structured metadata alongside the rendered text:

```python
class StudyNotes(BaseModel):
    title: str
    executive_summary: str
    key_terms: list[KeyTerm]
    sections: list[NoteSection]
    citations: list[Citation]

class NoteSection(BaseModel):
    heading: str
    takeaways: list[str]
    explanation: str
    citations: list[Citation]
```

Generation rules:

- Require an executive summary, key terms, and section takeaways.
- Keep each claim tied to one or more supplied chunks.
- Preserve uncertainty and explicitly mark missing information.
- Limit output length so a local model cannot monopolize memory.
- Validate headings and reject empty sections.
- Provide a deterministic extractive fallback that uses headings and important sentences when no LLM is available.

### Flashcard schema

```python
class Flashcard(BaseModel):
    front: str
    back: str
    difficulty: Literal["easy", "medium", "hard"]
    citations: list[Citation]
```

Flashcard rules:

- One concept per card.
- The front must not contain the answer verbatim unless the card type requires it.
- The back must be concise and grounded.
- Remove duplicates by normalized front text.
- Reject cards without citations.
- Store a stable card ID and source hash.
- Support basic card types first: definition, question/answer, and cloze deletion.

### Quiz schema

```python
class QuizQuestion(BaseModel):
    question: str
    question_type: Literal["multiple_choice", "true_false", "short_answer"]
    options: list[str]
    correct_answer: str
    explanation: str
    citations: list[Citation]
```

Quiz validation must check:

- Multiple-choice questions have exactly one correct option.
- Options are distinct and not empty.
- The answer key points to an existing option.
- True/false questions have exactly two valid choices.
- Short-answer questions include an accepted answer or grading rubric.
- Explanations do not introduce unsupported facts.
- Questions are not duplicates of existing cards.

If structured generation fails, retry once with the validation error summarized. If it still fails, use a local deterministic generator or return a visible failure instead of accepting malformed JSON.

### Phase 4 acceptance criteria

- Notes, flashcards, and quizzes validate before persistence.
- Every generated item has traceable citations.
- Duplicate cards and quiz questions are removed.
- Invalid model output cannot crash the session or enter the database.
- Local-only generation works when the configured local model is available.
- An extractive fallback exists for notes and basic cards.
- Exported Markdown and JSON can be regenerated from stored records.

Phase 5: SQLite Persistence, Review Scheduling & Local Audio
============================================================

### Objective

Persist documents, indexes, conversations, generated study artifacts, and review progress in one local SQLite database with explicit migrations. Use free local/browser speech rather than paid TTS services in the first version.

### SQLite schema layout

```text
sources
  id, source_type, uri, display_name, checksum, language, created_at, status
ingestion_jobs
  id, source_id, stage, progress, error_code, started_at, finished_at
chunks
  id, source_id, ordinal, text, page_start, page_end, time_start, time_end, text_hash
embeddings
  chunk_id, model_name, dimension, vector_blob, created_at
conversations
  id, source_scope, created_at, title
messages
  id, conversation_id, role, content, created_at
notes
  id, source_scope, title, markdown, config_hash, created_at
flashcards
  id, source_scope, front, back, difficulty, citations_json, created_at
reviews
  id, flashcard_id, due_at, interval_days, ease, repetitions, lapses, last_grade
quiz_sessions
  id, source_scope, score, total, created_at
quiz_questions
  id, quiz_session_id, question_json, selected_answer, is_correct
artifacts
  id, type, source_scope, content_hash, path, created_at
```

Use foreign keys, indexes on source and due date, UTC timestamps, and a migration table. Keep vector storage replaceable so a later Chroma or ANN adapter does not alter application records.

### Repository and transaction rules

- Access SQLite through repository functions, not scattered SQL in UI code.
- Use one write transaction for each ingestion/indexing job completion.
- Use WAL mode for responsive local reads during generation.
- Keep migrations forward-only and test them from an empty database.
- Store citations as validated JSON or normalized citation rows; never store unserialized Python objects.
- Use content hashes to prevent duplicate sources and artifacts.
- Make delete behavior explicit: source deletion removes dependent chunks, embeddings, notes, cards, and reviews only after confirmation.

### Free spaced repetition

Implement a small SM-2-style scheduler rather than adding a paid service. The minimum review grades are:

- Again: reset interval and increment lapse count.
- Hard: small interval increase.
- Good: normal interval increase.
- Easy: larger interval and ease increase.

Store interval, ease, repetitions, lapses, last grade, and next due date. Keep scheduling pure and unit-testable. Do not let an LLM choose due dates.

### Local audio output

Avoid ElevenLabs/OpenAI TTS in the cost-free baseline. Use one of these options:

1. Browser `SpeechSynthesis` for immediate, zero-download playback in the frontend.
2. The host operating system's installed TTS through a thin adapter.
3. Optional local Piper voice models when offline audio files are required.

Audio output is a presentation feature, not a prerequisite for notes, cards, or quizzes. Cache generated audio by content hash and voice configuration. Never upload study text merely to synthesize speech.

### Phase 5 acceptance criteria

- A clean database can be migrated to the current schema.
- A full source can be deleted without orphaned chunks or embeddings.
- Conversations and generated artifacts survive restart.
- Flashcard review grades produce deterministic due dates.
- Notes and cards can be exported without network access.
- Browser/system TTS works without a paid provider.
- Database backups can be copied and restored locally.

Phase 6: Fast Local Frontend & End-to-End System Layout
=======================================================

### Objective

Provide a fast local interface around the completed pipeline. Use Streamlit for the first MVP because it minimizes frontend code and has built-in file upload, session state, progress, and download controls. Keep service boundaries clean so a Next.js frontend can be added later without rewriting the core.

### Recommended local layout

```text
study-assistant/
  pyproject.toml
  requirements.txt
  .env.example
  README.md
  mind.md
  src/study_assistant/
    __init__.py
    config.py
    errors.py
    logging.py
    contracts.py
    ingestion/
      __init__.py
      orchestrator.py
      pdf_loader.py
      media_loader.py
      youtube_loader.py
      transcription.py
      normalize.py
      chunking.py
    embeddings/
      __init__.py
      base.py
      local_sentence_transformer.py
      index_repository.py
      fts_repository.py
    retrieval/
      __init__.py
      lexical.py
      semantic.py
      fusion.py
      context.py
      citations.py
    generation/
      __init__.py
      base.py
      local_provider.py
      prompts.py
      validators.py
      notes.py
      flashcards.py
      quizzes.py
    persistence/
      __init__.py
      db.py
      migrations/
      repositories.py
      review_scheduler.py
    audio/
      __init__.py
      browser_tts.py
      local_tts.py
    ui/
      __init__.py
      app.py
      pages/
        ingest.py
        library.py
        tutor.py
        notes.py
        flashcards.py
        quizzes.py
      components/
        progress.py
        citations.py
        cards.py
  tests/
    fixtures/
    test_ingestion.py
    test_chunking.py
    test_embeddings.py
    test_retrieval.py
    test_schemas.py
    test_reviews.py
    test_end_to_end.py
  var/
    .gitkeep
```

### Streamlit screens

**1. Library / dashboard**

- Show indexed sources, status, language, page/duration counts, and last updated time.
- Provide delete, rebuild, and inspect actions.
- Show whether the local embedding and generation models are available.

**2. Ingest**

- Upload PDF or audio files.
- Paste one permitted YouTube URL.
- Show stage-by-stage progress: validation, download, transcription, parsing, chunking, indexing.
- Display errors without losing completed staging data.
- Allow cancellation.

**3. Tutor**

- Scope the question to one source, a subject, or the entire library.
- Show grounded answer, citations, retrieval confidence, and model/provider label.
- Show retrieved evidence on demand.
- Offer a clear insufficient-evidence response.

**4. Notes**

- Select a source or section.
- Generate, validate, edit, save, and export Markdown notes.
- Display page/timestamp citations beside claims.

**5. Flashcards**

- Review one card at a time.
- Reveal answer, grade Again/Hard/Good/Easy, and show due date.
- Filter by source, difficulty, and due status.
- Export cards as JSON or CSV.

**6. Quizzes**

- Choose question count and type.
- Generate validated questions from selected material.
- Record answers and score in SQLite.
- Show explanations and citations after submission.

### State and background jobs

Keep long-running ingestion and generation work out of the synchronous UI callback. Use a local job manager with a thread/process worker appropriate to the workload:

- Download and parsing can use worker threads.
- Whisper and embedding work may use one bounded process to avoid memory contention.
- SQLite writes remain serialized.
- UI polls structured job progress by job ID.
- A restart marks abandoned jobs as interrupted and provides a resume/retry action.

Do not introduce Celery, Redis, Kubernetes, or hosted queues in the cost-free MVP. Add them only if measured concurrency requires them.

### End-to-end test path

Use a small fixture document and short audio file to validate:

```text
upload source
  -> Phase 1 staged text/chunks
  -> local embeddings/index
  -> hybrid retrieval
  -> grounded tutor answer
  -> cited notes
  -> flashcards
  -> quiz and score
  -> SQLite persistence
  -> local/browser audio playback
```

The end-to-end test must run without network after models are cached. Network-dependent YouTube and first-time model downloads should be separate integration tests, not prerequisites for every test run.

### Phase 6 acceptance criteria

- The app starts locally with one documented command.
- A user can ingest a fixture PDF without a paid account.
- A user can ask a grounded question and inspect citations.
- Notes, cards, quizzes, scores, and review dates persist across restart.
- Long operations show progress and support cancellation.
- No raw study material is sent to a network service in the free-local configuration.
- Export and backup work without cloud dependencies.
- The complete fixture path is covered by automated tests.

Fastest free implementation order
==================================

Build vertically rather than completing each subsystem in isolation:

1. Phase 1 PDF ingestion -> deterministic chunks.
2. Phase 2 local embeddings -> SQLite/NumPy index.
3. Phase 3 lexical retrieval first, then semantic retrieval, then local generation.
4. Phase 4 notes first, flashcards second, quizzes third.
5. Phase 5 SQLite persistence and review scheduler.
6. Phase 6 Streamlit ingest page and tutor page.
7. Add YouTube, audio TTS, richer quiz types, and UI polish after the PDF-to-answer path is stable.

Free/cost-control policy
========================

- No Pinecone, AWS S3, OpenAI embeddings, OpenAI TTS, ElevenLabs, or hosted queues in the baseline.
- Network is needed only for first-time model downloads and explicitly requested YouTube ingestion.
- Local models are cached and versioned.
- Cloud providers may be added later through interfaces without changing stored contracts.
- Large models, GPU acceleration, ANN indexes, and remote deployment are optimization choices, not Phase 1 prerequisites.

Full-system definition of done
==============================

Planning is complete when every phase has a contract, local default, failure path, test strategy, and acceptance criteria. Implementation is intentionally gated: Phase 1 is implemented first, and model names, supported file limits, retention policy, local LLM choice, and frontend choice are finalized at the gate for the phase that needs them.

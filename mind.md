1.ARCHITECTURE

1. Ingestion & Transformation Subsystem
 * Multi-Source Loaders: Ingests unstructured inputs—PDF/DOCX via pypdf/unstructured, YouTube audio tracks via yt-dlp, and audio recordings.
 * Speech-to-Text Pipeline: Routes raw audio directly into OpenAI Whisper or local faster-whisper to generate clean, timestamped text transcripts.
 * Deterministic Chunking: Feeds sanitized text through LangChain’s RecursiveCharacterTextSplitter (chunk size ~800 tokens, 15% overlap) to preserve semantic boundaries.
 * Vectorization Engine: Maps chunks into high-dimensional vectors via an embedding model (e.g., text-embedding-3-small or nomic-embed-text).
2. Storage & Semantic Indexing Subsystem
 * Vector Database (Pinecone / ChromaDB): Stores text embeddings alongside metadata dictionaries (source document, page/timestamp, section header, session ID).
 * Relational Cache (SQLite / PostgreSQL): Stores user sessions, quiz scores, spaced repetition flashcard review schedules, and raw conversation history.
 * File Storage: Local directory or AWS S3 bucket to persist uploaded study assets for retrieval and inspection.
3. Orchestration & Retrieval Engine (RAG Pipeline)
 * Hybrid Search / Retriever: Converts queries into embeddings and queries the vector index via cosine similarity, pulling the top-k (3–5) chunks.
 * Context Formatter: Stitches retrieved chunks, source metadata, and the conversation buffer into a structured prompt template.
 * System Guardrails: Enforces a strict grounding instruction (Answer strictly based on the provided context. If the source material does not contain the answer, state that it is missing).
4. Generation & Tool Execution Subsystem
 * LLM Core (GPT-4o / Gemini 1.5 Pro): Dispatches context payloads depending on the selected task.
 * Structured Outputs: Uses Pydantic JSON schemas or function calling to force the model to output valid, parseable arrays for:
   * Flashcards: [{"front": "Term/Concept", "back": "Definition/Explanation"}]
   * Quizzes: [{"question": "...", "options": ["A", "B", "C", "D"], "correct_answer": "...", "explanation": "..."}]
   * Notes: Hierarchical Markdown with executive takeaways and definitions.
 * Audio Synthesis (TTS): Directs generated text to ElevenLabs or OpenAI TTS to produce voice outputs.
5. Application & Presentation Layer
 * Frontend UI: Streamlit, Gradio, or a lightweight Next.js/Tailwind frontend with file upload zones, an interactive chat window, and flashcard swipe decks.
 * State Management: Tracks session states, active documents, and interactive quiz scoring.

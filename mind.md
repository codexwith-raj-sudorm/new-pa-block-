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

2.PHASES

Phase 1: Environment Setup & Document Ingestion
 * Environment Provisioning: Initialize a modular Python project, configure virtual environments, and install document parsing libraries (pypdf, python-docx, unstructured).
 * Audio & Video Processing: Integrate yt-dlp and faster-whisper to download lecture audio and generate timestamped transcriptions.
 * Deterministic Text Splitting: Implement LangChain's RecursiveCharacterTextSplitter configured for 800-token chunks with 10–15% overlap to maintain contextual continuity across chunk boundaries.
Phase 2: Embedding Generation & Vector Database Indexing
 * Embedding Model Integration: Connect to an embedding pipeline (such as OpenAI text-embedding-3-small or local Ollama embeddings).
 * Vector Store Setup: Initialize Pinecone or ChromaDB, defining vector dimensions, distance metrics (cosine similarity), and index namespaces per subject or document.
 * Metadata Attachment: Upsert chunk vectors alongside structured metadata payloads containing document_name, page_number, timestamp, and chunk_id.
Phase 3: Core Retrieval-Augmented Generation (RAG) Engine
 * Semantic Query Formulation: Convert incoming user questions into query vectors using the exact embedding model used during ingestion.
 * Similarity Search & Ranking: Query the vector index to extract the top-k (3–5) chunks based on similarity scores.
 * Context Assembly & Grounding: Inject retrieved chunks into a strict system prompt template that instructs the LLM to answer solely based on the provided material, suppressing external hallucinations.
Phase 4: Structured Generators (Notes, Flashcards & Quizzes)
 * Smart Summarization: Build dedicated LLM prompt chains to produce structured Markdown notes featuring executive summaries, key terms, and bulleted takeaways.
 * Structured Output Enforcement: Use Pydantic schemas or function calling to force the model into outputting validated JSON arrays for flashcards (front/back pairs).
 * Quiz Engine: Implement schemas to generate multiple-choice, true/false, and short-answer questions complete with answer keys and explanations.
Phase 5: Voice Synthesis & Local Persistence
 * Text-to-Speech Integration: Connect OpenAI TTS or ElevenLabs to synthesize generated notes and flashcard solutions into streamable audio.
 * Relational Storage: Set up a lightweight SQLite database to persist user session history, active study topics, and document metadata.
 * Review Scheduling: Build spaced repetition tracking (e.g., an SM-2 algorithm or simple review intervals) to track quiz scores and schedule flashcards for review.
Phase 6: Frontend Development & System Integration
 * User Interface Construction: Build an interactive UI using Streamlit or Gradio with drag-and-drop file upload zones, an AI tutor chat pane, and a dynamic flashcard review deck.
 * State Management: Maintain session states for active documents, chat message history, and quiz progress.
 * End-to-End Testing: Validate the complete loop from raw document upload to audio-enabled quiz generation.


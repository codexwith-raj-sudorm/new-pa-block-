"""Streamlit MVP covering ingest, tutor, artifacts, and review."""

from __future__ import annotations

import os
from pathlib import Path


def main() -> None:
    try:
        import streamlit as st
        import streamlit.components.v1 as components
    except ImportError as exc:  # pragma: no cover - optional UI dependency
        raise SystemExit("Install the UI extra first: pip install -e '.[ui,embeddings]'") from exc

    from ..audio import speech_synthesis_html
    from ..embeddings import SemanticIndex, SentenceTransformerProvider
    from ..errors import StudyAssistantError
    from ..generation import ArtifactGenerator, ExtractiveGenerator, OllamaProvider
    from ..ingestion.service import IngestionService
    from ..persistence import StudyDatabase
    from ..retrieval import HybridRetriever, RetrievalConfig

    st.set_page_config(page_title="Local Study Assistant", page_icon="📚", layout="wide")
    st.title("📚 Local Study Assistant")
    st.caption("Free-first, local-first study tools. Cloud APIs are not required.")

    root = Path(os.environ.get("STUDY_ASSISTANT_HOME", "var"))
    staging_root = root / "staging"
    index_path = root / "index" / "metadata.sqlite"
    study_db_path = root / "study.sqlite"
    with st.sidebar:
        st.header("Local configuration")
        model_name = st.text_input("Embedding model", value=os.environ.get("STUDY_ASSISTANT_EMBEDDING_MODEL", "all-MiniLM-L6-v2"))
        device = st.selectbox("Embedding device", ["cpu", "cuda"], index=0)
        ollama_model = st.text_input("Optional Ollama model", value=os.environ.get("STUDY_ASSISTANT_OLLAMA_MODEL", "llama3.2:3b"))
        st.info("The first embedding use may download the selected local model. No study text is sent to a cloud service.")

    db = StudyDatabase(study_db_path)
    try:
        sources = db.list_sources()
        tabs = st.tabs(["Library", "Ingest", "Tutor", "Notes", "Flashcards", "Quiz"])
        with tabs[0]:
            _library_tab(st, sources, db)
        with tabs[1]:
            _ingest_tab(st, StudyDatabase, IngestionService, SemanticIndex, SentenceTransformerProvider,
                        db, staging_root, index_path, model_name, device)
        with tabs[2]:
            _tutor_tab(st, components, HybridRetriever, SentenceTransformerProvider, ExtractiveGenerator,
                       OllamaProvider, index_path, model_name, device, ollama_model, sources)
        with tabs[3]:
            _artifact_tab(st, "notes", HybridRetriever, SentenceTransformerProvider, ArtifactGenerator,
                         db, index_path, model_name, device, sources)
        with tabs[4]:
            _flashcard_tab(st, db, HybridRetriever, SentenceTransformerProvider, ArtifactGenerator,
                           index_path, model_name, device, sources)
        with tabs[5]:
            _artifact_tab(st, "quiz", HybridRetriever, SentenceTransformerProvider, ArtifactGenerator,
                         db, index_path, model_name, device, sources)
    finally:
        db.close()


def _library_tab(st, sources, db) -> None:
    st.subheader("Indexed sources")
    if not sources:
        st.info("No sources yet. Use Ingest to add a PDF, audio file, or permitted YouTube URL.")
        return
    st.dataframe(sources, use_container_width=True, hide_index=True)
    if st.button("Export study database JSON"):
        output = db.export_json(db.path.with_suffix(".json"))
        st.success(f"Exported {output}")


def _ingest_tab(st, db_cls, service_cls, index_cls, provider_cls, db, staging_root, index_path, model_name, device) -> None:
    st.subheader("Add study material")
    uploaded = st.file_uploader("PDF or audio file", type=["pdf", "wav", "mp3", "m4a", "flac", "ogg", "opus", "webm"])
    youtube_url = st.text_input("Or paste one permitted YouTube URL")
    if not st.button("Ingest and index", type="primary"):
        return
    service = service_cls(output_root=staging_root)
    try:
        if uploaded is not None:
            upload_dir = staging_root.parent / "uploads"
            upload_dir.mkdir(parents=True, exist_ok=True)
            path = upload_dir / uploaded.name
            path.write_bytes(uploaded.getvalue())
            result = service.ingest_pdf(path) if path.suffix.lower() == ".pdf" else service.ingest_audio(path)
        elif youtube_url.strip():
            result = service.ingest_youtube(youtube_url.strip())
        else:
            st.warning("Choose a file or enter a YouTube URL.")
            return
        db.register_document(result.document, status="staged")
        provider = provider_cls(model_name=model_name, device=device)
        with index_cls(index_path, provider) as index:
            index.index_chunks(result.chunks)
        db.register_document(result.document, status="indexed")
        st.success(f"Indexed {len(result.chunks)} chunks from {result.document.title}.")
    except StudyAssistantError as exc:
        st.error(str(exc))


def _provider(provider_cls, model_name, device):
    return provider_cls(model_name=model_name, device=device)


def _tutor_tab(st, components, retriever_cls, provider_cls, extractive_cls, ollama_cls, index_path, model_name, device, ollama_model, sources) -> None:
    st.subheader("Grounded tutor")
    question = st.text_area("Question", placeholder="Ask about the indexed material")
    use_ollama = st.checkbox("Use local Ollama if available", value=False)
    if not st.button("Ask") or not question.strip():
        return
    source_ids = _source_selector(st, sources, key="tutor_scope")
    try:
        provider = _provider(provider_cls, model_name, device)
        with retriever_cls(index_path, provider, RetrievalConfig()) as retriever:
            result = retriever.retrieve(question, source_ids=source_ids)
        generator = ollama_cls(model=ollama_model) if use_ollama else extractive_cls()
        try:
            answer = generator.answer(result)
        except StudyAssistantError as exc:
            st.warning(f"Local generator unavailable; showing evidence instead: {exc}")
            answer = extractive_cls().answer(result)
        st.markdown(answer.answer)
        st.caption(f"Provider: {answer.provider} | confidence: {answer.confidence}")
        for citation in answer.citations:
            st.markdown(f"- {citation.label()}")
        if answer.answer:
            components.html(__import__("study_assistant.audio", fromlist=["speech_synthesis_html"]).speech_synthesis_html(answer.answer), height=50)
        with st.expander("Retrieved context"):
            st.text(result.context or "No evidence retrieved.")
    except StudyAssistantError as exc:
        st.error(str(exc))


def _artifact_tab(st, kind, retriever_cls, provider_cls, artifact_cls, db, index_path, model_name, device, sources) -> None:
    st.subheader("Generate " + ("study notes" if kind == "notes" else "a quiz"))
    query = st.text_area("Material or topic", key=f"{kind}_query")
    if not st.button("Generate", key=f"{kind}_button") or not query.strip():
        return
    source_ids = _source_selector(st, sources, key=f"{kind}_scope")
    try:
        provider = _provider(provider_cls, model_name, device)
        with retriever_cls(index_path, provider) as retriever:
            result = retriever.retrieve(query, source_ids=source_ids)
        artifacts = artifact_cls()
        if kind == "notes":
            notes = artifacts.notes(result)
            db.save_notes(notes, source_scope=source_ids[0] if len(source_ids) == 1 else "library")
            st.markdown(notes.to_markdown())
        else:
            quiz = artifacts.quiz(result)
            quiz_id = db.save_quiz(quiz, source_scope=source_ids[0] if len(source_ids) == 1 else "library")
            answers = {}
            for number, question in enumerate(quiz.questions, start=1):
                st.markdown(f"**{number}. {question.question}**")
                answers[number - 1] = st.radio("Answer", question.options, key=f"{question.question_id}")
                st.caption(f"Citation: {question.citations[0].label()}")
            if st.button("Submit quiz", key=f"submit-{quiz_id}"):
                score, total = db.submit_quiz(quiz_id, answers)
                st.success(f"Score: {score}/{total}")
    except StudyAssistantError as exc:
        st.error(str(exc))


def _flashcard_tab(st, db, retriever_cls, provider_cls, artifact_cls, index_path, model_name, device, sources) -> None:
    st.subheader("Flashcards")
    query = st.text_area("Topic for new cards", key="flashcard_query")
    if st.button("Generate flashcards") and query.strip():
        source_ids = _source_selector(st, sources, key="flashcard_scope")
        try:
            provider = _provider(provider_cls, model_name, device)
            with retriever_cls(index_path, provider) as retriever:
                retrieval = retriever.retrieve(query, source_ids=source_ids)
            cards = artifact_cls().flashcards(retrieval)
            saved = db.save_flashcards(cards, source_scope=source_ids[0] if len(source_ids) == 1 else "library")
            st.success(f"Saved {saved} flashcards.")
        except Exception as exc:
            st.error(str(exc))
    st.subheader("Due flashcards")
    cards = db.due_flashcards(limit=20)
    if not cards:
        st.info("No due cards. Generate notes/cards from the Tutor or Notes workflow first.")
        return
    for card in cards:
        with st.expander(card["front"]):
            st.write(card["back"])
            columns = st.columns(4)
            for column, grade in zip(columns, ("again", "hard", "good", "easy")):
                if column.button(grade.title(), key=f"{card['card_id']}-{grade}"):
                    updated = db.review_card(str(card["card_id"]), grade)
                    st.success(f"Next review: {updated.due_at}")


def _source_selector(st, sources, key: str) -> list[str]:
    options = {str(source["display_name"]): str(source["source_id"]) for source in sources}
    selected = st.multiselect("Limit to sources (optional)", list(options), key=key)
    return [options[name] for name in selected]


if __name__ == "__main__":  # pragma: no cover
    main()

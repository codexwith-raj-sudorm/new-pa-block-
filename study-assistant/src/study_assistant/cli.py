"""Command-line entry points for every local study-assistant phase."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .embeddings import SemanticIndex, SentenceTransformerProvider, load_staged_chunks
from .embeddings.contracts import IndexProgress
from .embeddings.providers import HashEmbeddingProvider
from .environment import collect_environment_metadata
from .contracts import SourceRef, TextChunk
from .errors import StudyAssistantError
from .generation import ArtifactGenerator, ExtractiveGenerator, OllamaProvider
from .ingestion.service import IngestionService
from .persistence import StudyDatabase
from .retrieval import HybridRetriever


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local-first study assistant")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for source_type in ("pdf", "audio", "youtube"):
        subparser = subparsers.add_parser(source_type)
        subparser.add_argument("source", help="local path or permitted YouTube URL")
        subparser.add_argument("--output-root", default="var/staging", help="local staging directory")
        subparser.add_argument("--study-database", default="var/study.sqlite", help="local study-state database")
    doctor = subparsers.add_parser("doctor", help="print local runtime metadata")
    doctor.add_argument("--output-root", default="var/staging", help="staging directory")
    demo = subparsers.add_parser("demo", help="run an offline end-to-end fixture demo")
    demo.add_argument("--root", default="var/demo", help="demo output directory")

    index = subparsers.add_parser("index", help="embed a Phase 1 staging directory locally")
    index.add_argument("stage_directory", help="directory containing chunks.jsonl")
    index.add_argument("--study-database", default="var/study.sqlite", help="local study-state database")
    _add_embedding_options(index)
    index.add_argument("--rebuild", action="store_true", help="clear the existing model index first")

    search = subparsers.add_parser("search", help="search a local semantic index")
    search.add_argument("query")
    search.add_argument("--top-k", type=int, default=5)
    search.add_argument("--source-id", action="append", dest="source_ids")
    _add_embedding_options(search)

    for command, description in (("ask", "answer from indexed evidence"), ("notes", "generate cited notes"),
                                 ("flashcards", "generate cited flashcards"), ("quiz", "generate a cited quiz")):
        artifact = subparsers.add_parser(command, help=description)
        artifact.add_argument("query")
        artifact.add_argument("--source-id", action="append", dest="source_ids")
        artifact.add_argument("--database", default="var/index/metadata.sqlite")
        artifact.add_argument("--study-database", default="var/study.sqlite")
        _add_embedding_options(artifact, include_database=False)
        artifact.add_argument("--ollama", action="store_true", help="use a local Ollama model for ask")
        artifact.add_argument("--ollama-model", default="llama3.2:3b")

    review = subparsers.add_parser("review", help="grade one due flashcard")
    review.add_argument("card_id")
    review.add_argument("grade", choices=["again", "hard", "good", "easy"])
    review.add_argument("--study-database", default="var/study.sqlite")
    export = subparsers.add_parser("export", help="export or back up local study state")
    export.add_argument("output", help=".json export or .sqlite backup destination")
    export.add_argument("--study-database", default="var/study.sqlite")
    sources = subparsers.add_parser("sources", help="list persisted sources")
    sources.add_argument("--study-database", default="var/study.sqlite")
    return parser


def _add_embedding_options(parser: argparse.ArgumentParser, *, include_database: bool = True) -> None:
    if include_database:
        parser.add_argument("--database", default="var/index/metadata.sqlite", help="SQLite index path")
    parser.add_argument("--model", default="all-MiniLM-L6-v2", help="local Sentence Transformer name")
    parser.add_argument("--device", default="cpu", help="Sentence Transformer device, default: cpu")
    parser.add_argument("--cache-folder", default=None, help="optional local model cache directory")
    parser.add_argument("--batch-size", type=int, default=16)


def _provider(args: argparse.Namespace) -> SentenceTransformerProvider:
    return SentenceTransformerProvider(model_name=args.model, device=args.device,
                                       cache_folder=args.cache_folder, batch_size=args.batch_size)


def _print_index_progress(event: IndexProgress) -> None:
    print(f"embedding {event.source_id}: {event.completed_chunks}/{event.total_chunks} ({event.elapsed_seconds:.1f}s)", file=sys.stderr)


def _register_staged_source(stage_directory: str, study_database: str, status: str) -> None:
    ingest_path = Path(stage_directory) / "ingest.json"
    data = json.loads(ingest_path.read_text(encoding="utf-8"))
    source_data = data["source"]
    source = SourceRef(
        source_id=str(source_data["source_id"]), source_type=source_data["source_type"],
        original_uri=str(source_data["original_uri"]), display_name=str(source_data["display_name"]),
        checksum=source_data.get("checksum"),
    )
    with StudyDatabase(study_database) as db:
        db.register_source(
            source, title=str(data.get("title") or source.display_name),
            language=data.get("language"), status=status,
            page_count=data.get("page_count"), duration_seconds=data.get("duration_seconds"),
        )


def _retrieve(args: argparse.Namespace):
    provider = _provider(args)
    retriever = HybridRetriever(args.database, provider)
    try:
        return retriever.retrieve(args.query, source_ids=args.source_ids)
    finally:
        retriever.close()


def _run_demo(root: str) -> None:
    demo_root = Path(root)
    demo_root.mkdir(parents=True, exist_ok=True)
    index_path = demo_root / "index.sqlite"
    study_path = demo_root / "study.sqlite"
    provider = HashEmbeddingProvider(dimension=48)
    chunks = (
        TextChunk("demo-0", "demo-source", "Photosynthesis converts light energy into chemical energy in plants.", 0, page_start=1, metadata={"display_name": "offline-demo.txt"}),
        TextChunk("demo-1", "demo-source", "Chlorophyll absorbs light, especially in the blue and red parts of the spectrum.", 1, page_start=1, metadata={"display_name": "offline-demo.txt"}),
        TextChunk("demo-2", "demo-source", "Cellular respiration releases usable energy from food molecules.", 2, page_start=2, metadata={"display_name": "offline-demo.txt"}),
    )
    with SemanticIndex(index_path, provider) as index:
        index.rebuild(chunks)
    with HybridRetriever(index_path, provider) as retriever:
        retrieval = retriever.retrieve("What does photosynthesis convert?")
    answer = ExtractiveGenerator().answer(retrieval)
    artifacts = ArtifactGenerator()
    notes = artifacts.notes(retrieval)
    cards = artifacts.flashcards(retrieval)
    quiz = artifacts.quiz(retrieval)
    with StudyDatabase(study_path) as db:
        db.save_flashcards(cards)
        db.save_notes(notes)
        quiz_id = db.save_quiz(quiz)
    print(f"Demo index: {index_path}")
    print(f"Study database: {study_path}")
    print(f"Retrieved chunks: {len(retrieval.chunks)}")
    print(f"Answer mode: {answer.provider}")
    print(answer.answer)
    print(f"Saved cards: {len(cards)} | quiz: {quiz_id}")


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "doctor":
            print(json.dumps(collect_environment_metadata(args.output_root), indent=2, sort_keys=True))
            return 0
        if args.command == "demo":
            _run_demo(args.root)
            return 0
        if args.command in {"pdf", "audio", "youtube"}:
            service = IngestionService(output_root=args.output_root)
            result = service.ingest_pdf(args.source) if args.command == "pdf" else service.ingest_audio(args.source) if args.command == "audio" else service.ingest_youtube(args.source)
            with StudyDatabase(args.study_database) as study_db:
                study_db.register_document(result.document, status="staged")
            print(f"source_id: {result.document.source.source_id}\nchunks: {len(result.chunks)}\nstaging_dir: {result.staging_dir}")
            return 0
        if args.command == "index":
            chunks = load_staged_chunks(args.stage_directory)
            provider = _provider(args)
            with SemanticIndex(args.database, provider) as index:
                count = index.rebuild(chunks, batch_size=args.batch_size, progress=_print_index_progress) if args.rebuild else index.index_chunks(chunks, batch_size=args.batch_size, progress=_print_index_progress)
                _register_staged_source(args.stage_directory, args.study_database, "indexed")
                print(f"indexed_chunks: {count}\ndatabase: {args.database}")
            return 0
        if args.command == "search":
            provider = _provider(args)
            with SemanticIndex(args.database, provider) as index:
                results = index.search(args.query, top_k=args.top_k, source_ids=args.source_ids)
            for result in results:
                print(json.dumps(result.__dict__, ensure_ascii=False, sort_keys=True))
            return 0
        if args.command in {"ask", "notes", "flashcards", "quiz"}:
            retrieval = _retrieve(args)
            if args.command == "ask":
                generator = OllamaProvider(model=args.ollama_model) if args.ollama else ExtractiveGenerator()
                try:
                    answer = generator.answer(retrieval)
                except StudyAssistantError as exc:
                    print(f"local generator unavailable: {exc}; using evidence fallback", file=sys.stderr)
                    answer = ExtractiveGenerator().answer(retrieval)
                print(answer.answer)
                print("\nCitations:")
                for citation in answer.citations:
                    print(f"- {citation.label()}")
                print(f"\nProvider: {answer.provider} | confidence: {answer.confidence}")
                return 0
            artifacts = ArtifactGenerator()
            with StudyDatabase(args.study_database) as db:
                if args.command == "notes":
                    notes = artifacts.notes(retrieval)
                    note_id = db.save_notes(notes, source_scope=args.source_ids[0] if args.source_ids and len(args.source_ids) == 1 else "library")
                    print(notes.to_markdown())
                    print(f"Saved note: {note_id}")
                elif args.command == "flashcards":
                    cards = artifacts.flashcards(retrieval)
                    count = db.save_flashcards(cards, source_scope=args.source_ids[0] if args.source_ids and len(args.source_ids) == 1 else "library")
                    print(json.dumps([card.__dict__ for card in cards], ensure_ascii=False, default=str, indent=2))
                    print(f"Saved flashcards: {count}")
                else:
                    quiz = artifacts.quiz(retrieval)
                    quiz_id = db.save_quiz(quiz, source_scope=args.source_ids[0] if args.source_ids and len(args.source_ids) == 1 else "library")
                    print(json.dumps({"quiz_id": quiz_id, "title": quiz.title, "questions": [question.__dict__ for question in quiz.questions]}, ensure_ascii=False, default=str, indent=2))
            return 0
        if args.command == "review":
            with StudyDatabase(args.study_database) as db:
                result = db.review_card(args.card_id, args.grade)
            print(json.dumps(result.__dict__, sort_keys=True))
            return 0
        if args.command == "sources":
            with StudyDatabase(args.study_database) as db:
                print(json.dumps(db.list_sources(), ensure_ascii=False, indent=2, default=str))
            return 0
        if args.command == "export":
            with StudyDatabase(args.study_database) as db:
                target = db.backup(args.output) if args.output.endswith(".sqlite") else db.export_json(args.output)
            print(target)
            return 0
    except StudyAssistantError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

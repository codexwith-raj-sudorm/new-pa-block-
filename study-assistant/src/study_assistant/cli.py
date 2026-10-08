"""Phase 1 and Phase 2 command-line entry points."""

from __future__ import annotations

import argparse
import json
import sys

from .embeddings import SemanticIndex, SentenceTransformerProvider, load_staged_chunks
from .embeddings.contracts import IndexProgress
from .environment import collect_environment_metadata
from .errors import StudyAssistantError
from .ingestion.service import IngestionService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local-first study assistant")
    subparsers = parser.add_subparsers(dest="command", required=True)
    for source_type in ("pdf", "audio", "youtube"):
        subparser = subparsers.add_parser(source_type)
        subparser.add_argument("source", help="local path or permitted YouTube URL")
        subparser.add_argument(
            "--output-root", default="var/staging", help="local staging directory"
        )
    doctor = subparsers.add_parser("doctor", help="print local runtime metadata")
    doctor.add_argument("--output-root", default="var/staging", help="staging directory")

    index = subparsers.add_parser("index", help="embed a Phase 1 staging directory locally")
    index.add_argument("stage_directory", help="directory containing chunks.jsonl")
    _add_embedding_options(index)
    index.add_argument("--rebuild", action="store_true", help="clear the existing model index first")

    search = subparsers.add_parser("search", help="search a local semantic index")
    search.add_argument("query")
    search.add_argument("--top-k", type=int, default=5)
    search.add_argument("--source-id", action="append", dest="source_ids")
    _add_embedding_options(search)
    return parser


def _add_embedding_options(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--database", default="var/index/metadata.sqlite", help="SQLite index path"
    )
    parser.add_argument("--model", default="all-MiniLM-L6-v2", help="local Sentence Transformer name")
    parser.add_argument("--device", default="cpu", help="Sentence Transformer device, default: cpu")
    parser.add_argument("--cache-folder", default=None, help="optional local model cache directory")
    parser.add_argument("--batch-size", type=int, default=16)


def _provider(args: argparse.Namespace) -> SentenceTransformerProvider:
    return SentenceTransformerProvider(
        model_name=args.model,
        device=args.device,
        cache_folder=args.cache_folder,
        batch_size=args.batch_size,
    )


def _print_index_progress(event: IndexProgress) -> None:
    print(
        f"embedding {event.source_id}: {event.completed_chunks}/{event.total_chunks} "
        f"({event.elapsed_seconds:.1f}s)",
        file=sys.stderr,
    )


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "doctor":
            print(json.dumps(collect_environment_metadata(args.output_root), indent=2, sort_keys=True))
            return 0
        if args.command in {"pdf", "audio", "youtube"}:
            service = IngestionService(output_root=args.output_root)
            if args.command == "pdf":
                result = service.ingest_pdf(args.source)
            elif args.command == "audio":
                result = service.ingest_audio(args.source)
            else:
                result = service.ingest_youtube(args.source)
            print(f"source_id: {result.document.source.source_id}")
            print(f"chunks: {len(result.chunks)}")
            print(f"staging_dir: {result.staging_dir}")
            return 0
        if args.command == "index":
            chunks = load_staged_chunks(args.stage_directory)
            provider = _provider(args)
            with SemanticIndex(args.database, provider) as semantic_index:
                if args.rebuild:
                    count = semantic_index.rebuild(
                        chunks, batch_size=args.batch_size, progress=_print_index_progress
                    )
                else:
                    count = semantic_index.index_chunks(
                        chunks, batch_size=args.batch_size, progress=_print_index_progress
                    )
                print(f"indexed_chunks: {count}")
                print(f"database: {args.database}")
            return 0
        if args.command == "search":
            provider = _provider(args)
            with SemanticIndex(args.database, provider) as semantic_index:
                results = semantic_index.search(
                    args.query, top_k=args.top_k, source_ids=args.source_ids
                )
            for result in results:
                print(json.dumps({
                    "chunk_id": result.chunk_id,
                    "source_id": result.source_id,
                    "score": result.score,
                    "ordinal": result.ordinal,
                    "page_start": result.page_start,
                    "page_end": result.page_end,
                    "start_seconds": result.start_seconds,
                    "end_seconds": result.end_seconds,
                    "text": result.text,
                    "metadata": result.metadata,
                }, ensure_ascii=False, sort_keys=True))
            return 0
    except StudyAssistantError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 2


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())

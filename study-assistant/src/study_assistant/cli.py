"""Small Phase 1 command-line entry point."""

from __future__ import annotations

import argparse
import json
import sys

from .environment import collect_environment_metadata
from .errors import StudyAssistantError
from .ingestion.service import IngestionService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local-first study assistant ingestion")
    subparsers = parser.add_subparsers(dest="source_type", required=True)
    for source_type in ("pdf", "audio", "youtube"):
        subparser = subparsers.add_parser(source_type)
        subparser.add_argument("source", help="local path or permitted YouTube URL")
        subparser.add_argument(
            "--output-root", default="var/staging", help="local staging directory"
        )
    doctor = subparsers.add_parser("doctor", help="print local runtime metadata")
    doctor.add_argument("--output-root", default="var/staging", help="staging directory")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.source_type == "doctor":
        print(json.dumps(collect_environment_metadata(args.output_root), indent=2, sort_keys=True))
        return 0

    service = IngestionService(output_root=args.output_root)
    try:
        if args.source_type == "pdf":
            result = service.ingest_pdf(args.source)
        elif args.source_type == "audio":
            result = service.ingest_audio(args.source)
        else:
            result = service.ingest_youtube(args.source)
    except StudyAssistantError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(f"source_id: {result.document.source.source_id}")
    print(f"chunks: {len(result.chunks)}")
    print(f"staging_dir: {result.staging_dir}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())

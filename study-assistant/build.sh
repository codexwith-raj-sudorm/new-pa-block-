#!/usr/bin/env bash
# Build the study assistant as an isolated Python wheel and source archive.
# Generated dist/ and build/ directories are intentionally ignored by Git.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
cd "$ROOT_DIR"

if ! "$PYTHON_BIN" -c 'import pytest, build' >/dev/null 2>&1; then
  echo "Build dependencies are missing. Run: $PYTHON_BIN -m pip install -e \".[dev]\"" >&2
  exit 2
fi

"$PYTHON_BIN" -m pytest
rm -rf build dist
"$PYTHON_BIN" -m build --sdist --wheel

printf '\nBuild complete:\n'
find dist -maxdepth 1 -type f -printf '  %f\n' | sort

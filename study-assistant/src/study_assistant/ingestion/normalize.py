"""Conservative normalization that keeps paragraph and transcript boundaries."""

from __future__ import annotations

import re
import unicodedata

_CONTROL_EXCEPTIONS = {"\n", "\t"}


def normalize_text(value: str) -> str:
    """Normalize Unicode and whitespace without flattening meaningful lines.

    Source offsets are calculated after normalization, so all loaders use this
    function before constructing their document and segment records.
    """

    value = unicodedata.normalize("NFKC", value)
    value = "".join(
        char
        for char in value
        if char in _CONTROL_EXCEPTIONS or not unicodedata.category(char).startswith("C")
    )
    value = value.replace("\r\n", "\n").replace("\r", "\n")

    lines: list[str] = []
    blank_lines = 0
    for raw_line in value.split("\n"):
        line = re.sub(r"[^\S\n]+", " ", raw_line).strip()
        if line:
            blank_lines = 0
            lines.append(line)
        else:
            blank_lines += 1
            # One empty line is enough to preserve a paragraph boundary;
            # collapsing longer runs keeps chunk offsets and output compact.
            if blank_lines <= 1:
                lines.append("")

    return "\n".join(lines).strip()


def normalize_title(value: str, fallback: str = "Untitled source") -> str:
    title = normalize_text(value).replace("\n", " ")
    title = re.sub(r"\s+", " ", title).strip()
    return title or fallback

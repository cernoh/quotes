#!/usr/bin/env python3
"""Validate the quote corpus: shape, uniqueness, and grounding rules.

Run directly, or through `nix flake check` (the `corpus` check).
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

WIKIQUOTE_PREFIX = "https://en.wikiquote.org/"
MIN_QUOTES = 40
MIN_AUTHORS = 10
MIN_TEXT = 40
MAX_TEXT = 400


def fail(problems: list[str], message: str) -> None:
    problems.append(message)


def main(argv: list[str]) -> int:
    path = Path(argv[1]) if len(argv) > 1 else Path("data/quotes.json")
    data = json.loads(path.read_text(encoding="utf-8"))
    problems: list[str] = []

    if not isinstance(data, dict) or "quotes" not in data:
        print(f"corpus: {path} must be an object with a 'quotes' list", file=sys.stderr)
        return 1

    quotes = data["quotes"]
    seen: dict[str, dict] = {}
    authors: set[str] = set()

    for index, quote in enumerate(quotes):
        where = f"quotes[{index}]"
        if not isinstance(quote, dict):
            fail(problems, f"{where}: not an object")
            continue
        for key in ("author", "work", "text", "source"):
            if key not in quote:
                fail(problems, f"{where}: missing key {key!r}")
        author = quote.get("author")
        work = quote.get("work")
        text = quote.get("text")
        source = quote.get("source")
        if not isinstance(author, str) or not author.strip():
            fail(problems, f"{where}: author must be a non-empty string")
            continue
        authors.add(author)
        if work is not None and not isinstance(work, str):
            fail(problems, f"{where}: work must be a string or null")
        if not isinstance(text, str):
            fail(problems, f"{where}: text must be a string")
            continue
        if not MIN_TEXT <= len(text) <= MAX_TEXT:
            fail(problems, f"{where}: text length {len(text)} outside {MIN_TEXT}-{MAX_TEXT}")
        if text != text.strip():
            fail(problems, f"{where}: text has leading or trailing whitespace")
        key = " ".join(text.casefold().split())
        if key in seen:
            fail(problems, f"{where}: duplicate text, first seen at {seen[key]['where']}")
        else:
            seen[key] = {"where": where}
        if not isinstance(source, str) or not source.startswith(WIKIQUOTE_PREFIX):
            fail(problems, f"{where}: source must be a {WIKIQUOTE_PREFIX}* URL")

    if len(quotes) < MIN_QUOTES:
        fail(problems, f"corpus holds {len(quotes)} quotes, expected at least {MIN_QUOTES}")
    if len(authors) < MIN_AUTHORS:
        fail(problems, f"corpus holds {len(authors)} authors, expected at least {MIN_AUTHORS}")

    if problems:
        for problem in problems:
            print(f"corpus: {problem}", file=sys.stderr)
        print(f"corpus: {len(problems)} problem(s)", file=sys.stderr)
        return 1

    print(f"corpus: {len(quotes)} quotes from {len(authors)} authors")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

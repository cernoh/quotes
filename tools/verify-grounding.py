#!/usr/bin/env python3
"""Prove that every quote in the corpus comes from the page it names.

Each corpus entry carries a `source` URL, normally a Wikiquote page. This script
downloads each distinct source once, normalises typographic punctuation and
whitespace on both sides, and then asserts that the quote `text` appears in the
page. A quote with no match is a grounding failure: it was written from memory,
paraphrased, or attributed to the wrong page.

This needs the network, so `nix flake check` does not run it. Run it by hand:

    nix run nixpkgs#python3 -- tools/verify-grounding.py
    nix run nixpkgs#python3 -- tools/verify-grounding.py --data data/quotes.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

PUNCTUATION = str.maketrans(
    {
        "\u2018": "'",
        "\u2019": "'",
        "\u201c": '"',
        "\u201d": '"',
        "\u2013": "-",
        "\u2014": "-",
        "\u2026": "...",
        "\u00a0": " ",
        "\u00ad": "",
    }
)


def normalise(text: str) -> str:
    """Fold the differences the wiki source and the corpus may disagree on."""
    folded = text.translate(PUNCTUATION)
    folded = folded.replace("\u2019", "'").replace("\u2014", "-").replace("\u2013", "-")
    folded = re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", folded)
    folded = folded.replace("'''", "").replace("''", "")
    folded = re.sub(r"\s+", " ", folded)
    return folded.strip().casefold()


def fetch(url: str) -> str:
    """Fetch the raw wikitext of a wiki page."""
    if "action=raw" not in url and "w/index.php" not in url:
        url = url.replace("/wiki/", "/w/index.php?action=raw&title=")
    request = urllib.request.Request(url, headers={"User-Agent": "quotes-grounding-check/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", default="data/quotes.json")
    args = parser.parse_args(argv[1:])

    quotes = json.loads(Path(args.data).read_text(encoding="utf-8"))["quotes"]
    pages: dict[str, str | None] = {}
    failures: list[str] = []

    for quote in quotes:
        url = quote["source"]
        if url not in pages:
            try:
                pages[url] = normalise(fetch(url))
                print(f"fetched {url}", file=sys.stderr)
            except Exception as error:  # network trouble is a failure, not a pass
                pages[url] = None
                print(f"fetch failed for {url}: {error}", file=sys.stderr)
        page = pages[url]
        if page is None:
            failures.append(f"{quote['author']}: page unreachable {url}")
            continue
        if normalise(quote["text"]) not in page:
            failures.append(f"{quote['author']} ({quote.get('work')}): {quote['text'][:70]!r}")

    if failures:
        print(f"\n{len(failures)} of {len(quotes)} quotes are not on their source page:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print(f"\nall {len(quotes)} quotes appear on their source page ({len(pages)} pages)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

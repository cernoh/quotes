# data

## Purpose

Owns the quote corpus `quotes.json`: the only source of quote text shown by the
widget.

## Ownership

`quotes.json` and this document. The widget reads the file and never writes it.

## Rights

- The 134 quotes come from 22 authors. 16 of those authors are in copyright,
  either in the work or in the English translation: Murakami, Mishima, Dazai,
  Baldwin, Beckett, Hemingway, Cioran, Hesse, Camus, Rilke, Woolf, Weil,
  Kierkegaard, Nietzsche, Kafka, and Sōseki (translation).
- Only Dostoevsky, Tolstoy, Chekhov, Turgenev, Marcus Aurelius, and Seneca are
  safely public domain.
- This means the corpus MUST NOT go to a store that checks bundled content
  (F-Droid main repository). It decided not to, on 2026-09-19.
- Add every new quote to this audit: a quote nobody can account for breaks a
  later redistribution decision.

## Local Contracts

- The file is an object: `{"version": 1, "note": "...", "quotes": [...]}`.
- Every entry holds exactly four keys: `author`, `work`, `text`, `source`.
- `author` is the English name of the writer, spelled the same way in every
  entry for that writer.
- `work` is the book, story, or essay title as the source page states it, or
  `null` when the page gives no work.
- `text` is the quote itself, 40 to 400 characters, no leading or trailing
  space, with typographic punctuation as the source has it.
- `source` is the English Wikiquote page URL the text came from, for example
  `https://en.wikiquote.org/wiki/Fyodor_Dostoyevsky`.

## Work Guidance

Adding a quote:

1. Fetch the raw page: `curl -sL 'https://en.wikiquote.org/w/index.php?title=<Author>&action=raw'`.
2. Copy a contiguous span of the author's own quote, character for character.
   Never type a quote from memory and never paraphrase.
3. Take the work title from the page. Use `null` when the page states none.
4. Skip the `Misattributed`, `Disputed`, `Quotes about`, `See also` and
   `External links` sections.
5. Prefer lines about living, enduring, struggling, loneliness and reflection.
6. Run `tools/check-corpus.py` and `tools/verify-grounding.py`.

A work can also be quoted on a theme page rather than on its own page: the
White Nights lines come from the `Night`, `Saint Petersburg` and `Beatitude`
pages, which name the work next to the quote. Record the page the text came
from in `source`.

Quote text that Wikiquote marks as misattributed, or that no page holds, does
not belong in this file, however well known it is.

## Verification

- `nix run nixpkgs#python3 -- tools/check-corpus.py` checks shape, key set, text
  length, duplicate text, `source` prefix and the minimum counts.
- `cd android && gradle copyCorpus` runs the same floor at build time and copies
  the corpus into the APK assets.
- `nix run nixpkgs#python3 -- tools/verify-grounding.py` fetches every source
  page and shows that each `text` appears in it. This needs the network, so no
  build runs it.

## Child DOX Index

None.

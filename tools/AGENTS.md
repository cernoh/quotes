# tools

## Purpose

Owns the repository checks that run outside the build sandbox.

## Ownership

`check-corpus.py` and `verify-grounding.py`.

## Local Contracts

- Both scripts take `--data` (`check-corpus.py`: a positional path) and exit
  non-zero on failure, printing one line per problem.
- `check-corpus.py` MUST stay offline. It runs by hand, and the Gradle
  `copyCorpus` task applies the same rules at build time.
- `verify-grounding.py` needs the network. It MUST stay out of every build and
  run by hand instead.

## Work Guidance

- Grounding comparison normalises typographic punctuation, wiki links, bold
  markup and whitespace. Widen that normalisation only for differences the wiki
  source and the corpus legitimately disagree on, never to excuse a rewritten
  quote.

## Verification

- `nix run nixpkgs#python3 -- tools/check-corpus.py` prints the quote and author
  counts, or one line per problem.
- `nix run nixpkgs#python3 -- tools/verify-grounding.py` prints the count of
  quotes and pages it checked.

## Child DOX Index

None.

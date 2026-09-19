# tools

## Purpose

Owns the repository checks that run outside the build sandbox.

## Ownership

`check-corpus.py` and `verify-grounding.py`.

## Local Contracts

- Both scripts take `--data` (`check-corpus.py`: a positional path) and exit
  non-zero on failure, printing one line per problem.
- `check-corpus.py` MUST stay offline: `nix flake check` runs it inside the Nix
  sandbox, where the network is unavailable.
- `verify-grounding.py` needs the network. It MUST stay out of `nix flake check`
  and run by hand instead.

## Work Guidance

- Grounding comparison normalises typographic punctuation, wiki links, bold
  markup and whitespace. Widen that normalisation only for differences the wiki
  source and the corpus legitimately disagree on, never to excuse a rewritten
  quote.

## Verification

- `nix flake check` runs the corpus check.
- `nix run nixpkgs#python3 -- tools/verify-grounding.py` runs the grounding
  check and prints the count of quotes and pages.

## Child DOX Index

None.

# quotes

A small desktop widget that puts one line from a classic book on the screen and
leaves it there: Dostoevsky, Murakami, Mishima, Kafka, Camus, Rilke, Marcus
Aurelius, Woolf, Baldwin, and others. Lines about staying alive, keeping on, and
getting through the day.

The widget is a GTK4 window placed by `gtk4-layer-shell` on a Wayland layer, so
it sits above the wallpaper like an ornament. One quote is visible at a time. It
changes on a timer and fades between quotes. A left click moves to the next
quote, a right click prints the current quote and quits.

## Run it

```sh
nix run github:cernoh/quotes                       # widget in the bottom right
nix run github:cernoh/quotes -- --anchor top-left  # somewhere else
nix run github:cernoh/quotes -- --print --seed 7    # one quote in the terminal
nix run github:cernoh/quotes -- --list             # authors and counts
```

From a checkout:

```sh
nix run .
nix develop      # python3 with pygobject, gtk4, gtk4-layer-shell, grim
```

## Options

| Flag | Default | Effect |
| --- | --- | --- |
| `--interval SECONDS` | `600` | time between quotes |
| `--anchor CORNER` | `bottom-right` | `bottom-right`, `bottom-left`, `top-right`, `top-left` |
| `--margin PIXELS` | `44` | distance from the screen edge |
| `--width PIXELS` | `470` | card width; the quote wraps inside it |
| `--font FAMILY` | `EB Garamond` | serif family; the bundled font is registered at startup |
| `--size PIXELS` | `21` | quote text size |
| `--layer LAYER` | `top` | Wayland layer: `background`, `bottom`, `top`, `overlay`. `top` keeps the card above windows; `bottom` makes it a desktop ornament that windows cover |
| `--author NAME` | - | only quotes whose author matches the substring |
| `--work TITLE` | - | only quotes from a book or essay matching the substring |
| `--interactive` | off | take keyboard focus on demand, so `n` and `q` work |
| `--seed N` | random | make the shuffle order repeatable |
| `--data PATH` | bundled | use another corpus file |
| `--print` | - | print one quote and exit |
| `--list` | - | print authors and counts, then exit |

## Keys and clicks

- left click: next quote
- right click: print the current quote to stdout, then quit
- `n`, space, right arrow: next quote (needs `--interactive`)
- `q`, escape: quit (needs `--interactive`)

## Corpus

`data/quotes.json` holds the corpus. Each entry has four fields:

```json
{
  "author": "Fyodor Dostoevsky",
  "work": "The Brothers Karamazov",
  "text": "For the mystery of human existence lies not in just staying alive but in finding something to live for.",
  "source": "https://en.wikiquote.org/wiki/Fyodor_Dostoyevsky"
}
```

The rule for this repository: every `text` value comes from the English
Wikiquote page named in `source`, character for character. No quote is written
from memory, and no quote is paraphrased. `work` names the book or essay the
line comes from; it is `null` when Wikiquote gives no work.

```sh
nix flake check                      # validates shape, uniqueness and grounding
python3 tools/check-corpus.py        # the same check, run directly
```

## Start it with the session

mango reads its config from a file, so a `spawn_shell` line runs the widget at
login:

```conf
spawn_shell = nix run github:cernoh/quotes -- --interval 900
```

For a stored build, run the wrapper from the profile instead of `nix run`:

```sh
nix profile install github:cernoh/quotes
```

## Layout

```
data/quotes.json        the corpus
quotes/widget.py        the GTK4 layer-shell widget and its CLI
tools/check-corpus.py   the corpus validator (offline, runs as a flake check)
tools/verify-grounding.py  fetches every source page and proves each quote is on it
flake.nix               package, app, dev shell, corpus check
```

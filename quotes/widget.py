#!/usr/bin/env python3
"""quotes - a Wayland desktop widget for a sourced line from a classic book.

The widget is a GTK4 window placed by gtk4-layer-shell on a Wayland layer, so
it floats above the wallpaper like a desktop ornament. It shows one quote at a
time, fades between quotes on a timer, and moves on when clicked.

Usage:
    quotes                       # show the widget
    quotes --print               # print one quote and exit
    quotes --list                # list authors and quote counts
"""

from __future__ import annotations

import argparse
import json
import os
import random
import signal
import sys
from pathlib import Path

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Gdk", "4.0")
gi.require_version("Gtk4LayerShell", "1.0")
from gi.repository import Gdk, Gio, GLib, Gtk, Pango  # noqa: E402
from gi.repository import Gtk4LayerShell as LayerShell  # noqa: E402

APP_ID = "dev.cernoh.quotes"
DEFAULT_FONT = "EB Garamond"
DEFAULT_INTERVAL = 600
DEFAULT_WIDTH = 470
DEFAULT_MARGIN = 44

LAYERS = {
    "background": LayerShell.Layer.BACKGROUND,
    "bottom": LayerShell.Layer.BOTTOM,
    "top": LayerShell.Layer.TOP,
    "overlay": LayerShell.Layer.OVERLAY,
}

CORNERS = {
    "bottom-right": (LayerShell.Edge.RIGHT, LayerShell.Edge.BOTTOM),
    "bottom-left": (LayerShell.Edge.LEFT, LayerShell.Edge.BOTTOM),
    "top-right": (LayerShell.Edge.RIGHT, LayerShell.Edge.TOP),
    "top-left": (LayerShell.Edge.LEFT, LayerShell.Edge.TOP),
}

CSS = """
window.quotes-window {
  background-color: transparent;
}

.quote-card {
  background-color: rgba(26, 21, 17, 0.74);
  border: 1px solid rgba(214, 181, 133, 0.20);
  border-radius: 16px;
  padding: 34px 36px 30px 36px;
  box-shadow: 0 12px 34px rgba(0, 0, 0, 0.34);
}

.quote-text {
  color: #F2EADC;
  line-height: 1.52;
}

.quote-rule {
  background-color: rgba(214, 181, 133, 0.42);
}

.quote-author {
  color: #D9B98A;
}

.quote-work {
  color: #9A8B79;
  font-style: italic;
}
"""


def load_corpus(path: Path | None = None) -> list[dict]:
    """Read the quote corpus. The file is found in the checkout or the store."""
    candidates: list[Path] = []
    if path is not None:
        candidates.append(path)
    elif os.environ.get("QUOTES_DATA_DIR"):
        candidates.append(Path(os.environ["QUOTES_DATA_DIR"]) / "quotes.json")
    here = Path(__file__).resolve()
    candidates += [here.parent.parent / "data" / "quotes.json"]
    for candidate in candidates:
        if candidate.is_file():
            with candidate.open(encoding="utf-8") as handle:
                data = json.load(handle)
            quotes = data["quotes"] if isinstance(data, dict) else data
            if not quotes:
                raise SystemExit(f"quotes: {candidate} holds no quotes")
            return quotes
    raise SystemExit(
        "quotes: cannot find data/quotes.json (set QUOTES_DATA_DIR)"
    )


def filter_quotes(quotes: list[dict], author: str | None, work: str | None = None) -> list[dict]:
    kept = list(quotes)
    if author:
        needle = author.casefold()
        kept = [q for q in kept if needle in q["author"].casefold()]
        if not kept:
            raise SystemExit(f"quotes: no quotes by an author matching {author!r}")
    if work:
        needle = work.casefold()
        kept = [q for q in kept if needle in (q.get("work") or "").casefold()]
        if not kept:
            raise SystemExit(f"quotes: no quotes from a work matching {work!r}")
    return kept


class Deck:
    """Shuffled cycles of the corpus, so no quote repeats back to back."""

    def __init__(self, quotes: list[dict], seed: int | None = None):
        self._quotes = quotes
        self._random = random.Random(seed)
        self._pile: list[dict] = []

    def _refill(self) -> None:
        self._pile = list(self._quotes)
        self._random.shuffle(self._pile)

    def take(self) -> dict:
        if not self._pile:
            self._refill()
        return self._pile.pop()


def register_fonts(font_map: Pango.FontMap) -> list[str]:
    """Load the bundled font files into Pango, so the family resolves anywhere.

    QUOTES_FONT_FILE holds a pathsep separated list of font files. The wrapper
    sets it, and the flake also puts the font directory on XDG_DATA_DIRS, so a
    missing file only costs us the fontconfig fallback.
    """
    loaded: list[str] = []
    for entry in os.environ.get("QUOTES_FONT_FILE", "").split(os.pathsep):
        path = Path(entry)
        if not path.is_file():
            continue
        try:
            font_map.add_font_file(str(path))
            loaded.append(path.name)
        except (GLib.Error, AttributeError, TypeError, ValueError) as error:
            print(f"quotes: cannot load font {path}: {error}", file=sys.stderr)
    return loaded


def describe(quote: dict | None) -> str:
    """One quote as two plain lines: the text, then author and work."""
    if not quote:
        return ""
    work = f" - {quote['work']}" if quote.get("work") else ""
    return f"{quote['text']}\n  {quote['author']}{work}"


class QuoteWidget(Gtk.ApplicationWindow):
    def __init__(self, app: Gtk.Application, options: argparse.Namespace, deck: Deck):
        super().__init__(application=app, title="quotes")
        self.options = options
        self.deck = deck
        self._fade_source: int | None = None
        self._interval_source: int | None = None
        self._current: dict | None = None

        self.add_css_class("quotes-window")
        self.set_default_size(options.width, -1)

        # Register the shipped font before any label resolves a family. The
        # window's Pango context gives the display font map, which is the same
        # map the labels use.
        loaded = register_fonts(self.get_pango_context().get_font_map())
        print(f"quotes: loaded fonts {', '.join(loaded) or 'none'}", file=sys.stderr)

        LayerShell.init_for_window(self)
        LayerShell.set_namespace(self, "quotes")
        LayerShell.set_layer(self, LAYERS[options.layer])
        LayerShell.set_exclusive_zone(self, -1)
        for edge in CORNERS[options.anchor]:
            LayerShell.set_anchor(self, edge, True)
        for edge in (LayerShell.Edge.RIGHT, LayerShell.Edge.LEFT,
                     LayerShell.Edge.BOTTOM, LayerShell.Edge.TOP):
            if edge not in CORNERS[options.anchor]:
                LayerShell.set_margin(self, edge, options.margin)
        LayerShell.set_keyboard_mode(
            self,
            LayerShell.KeyboardMode.ON_DEMAND
            if options.interactive
            else LayerShell.KeyboardMode.NONE,
        )

        self._build()

        click = Gtk.GestureClick()
        click.connect("pressed", self._on_click)
        self.add_controller(click)

        keys = Gtk.EventControllerKey()
        keys.connect("key-pressed", self._on_key)
        self.add_controller(keys)

        self._apply(self.deck.take(), animate=False)
        self._interval_source = GLib.timeout_add_seconds(
            options.interval, self._rotate
        )

    def _build(self) -> None:
        card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=16)
        card.add_css_class("quote-card")
        self.set_child(card)

        self.quote_label = Gtk.Label(wrap=True, xalign=0.0, justify=Gtk.Justification.LEFT)
        self.quote_label.add_css_class("quote-text")
        self.quote_label.set_wrap_mode(Pango.WrapMode.WORD_CHAR)
        card.append(self.quote_label)

        rule = Gtk.Separator()
        rule.add_css_class("quote-rule")
        rule.set_size_request(34, 1)
        rule.set_halign(Gtk.Align.START)
        card.append(rule)

        self.author_label = Gtk.Label(xalign=0.0, wrap=True)
        self.author_label.add_css_class("quote-author")
        attributes = Pango.AttrList()
        attributes.insert(Pango.attr_letter_spacing_new(1100))
        self.author_label.set_attributes(attributes)
        card.append(self.author_label)

        self.work_label = Gtk.Label(xalign=0.0, wrap=True)
        self.work_label.add_css_class("quote-work")
        card.append(self.work_label)

    def _apply(self, quote: dict, animate: bool) -> None:
        self._current = quote
        self.quote_label.set_text(f"\u201c{quote['text']}\u201d")
        self.author_label.set_text(quote["author"].upper())
        work = quote.get("work")
        self.work_label.set_text(work if work else "")
        self.work_label.set_visible(bool(work))
        if not animate:
            self.quote_label.set_opacity(1.0)
            self.author_label.set_opacity(1.0)
            self.work_label.set_opacity(1.0)
        # A journal trail of what the widget showed and when.
        print(describe(quote), file=sys.stderr, flush=True)

    def _set_opacity(self, value: float) -> None:
        for label in (self.quote_label, self.author_label, self.work_label):
            label.set_opacity(value)

    def _fade(self, target: float, done=None) -> None:
        if self._fade_source is not None:
            GLib.source_remove(self._fade_source)
            self._fade_source = None
        step = 0.09 if target > 0.5 else -0.16

        def tick() -> bool:
            current = self.quote_label.get_opacity() + step
            finished = current >= 1.0 if step > 0 else current <= 0.0
            self._set_opacity(1.0 if finished and step > 0 else max(0.0, current))
            if finished:
                self._fade_source = None
                if done is not None:
                    done()
                return GLib.SOURCE_REMOVE
            return GLib.SOURCE_CONTINUE

        self._fade_source = GLib.timeout_add(16, tick)

    def advance(self) -> None:
        quote = self.deck.take()
        self._fade(0.0, lambda: (self._apply(quote, animate=False), self._fade(1.0)))

    def _rotate(self) -> bool:
        self.advance()
        return GLib.SOURCE_CONTINUE

    def _on_click(self, _gesture, _n_press: int, _x: float, _y: float, button: int) -> None:
        if button == 3:
            print(describe(self._current), flush=True)
            self.get_application().quit()
        else:
            self.advance()

    def _on_key(self, _controller, keyval: int, _code: int, _state) -> bool:
        if keyval in (Gdk.KEY_q, Gdk.KEY_Escape):
            self.get_application().quit()
            return True
        if keyval in (Gdk.KEY_n, Gdk.KEY_space, Gdk.KEY_Right):
            self.advance()
            return True
        if keyval == Gdk.KEY_p:
            return True
        return False


class QuotesApp(Gtk.Application):
    def __init__(self, options: argparse.Namespace, quotes: list[dict]):
        # NON_UNIQUE keeps a second widget alive. A GtkApplication is otherwise
        # single-instance per session bus: a second run hands off to the first
        # and exits, which silently ignores the flags it was given.
        super().__init__(
            application_id=APP_ID, flags=Gio.ApplicationFlags.NON_UNIQUE
        )
        self.options = options
        self.quotes = quotes
        self.widget: QuoteWidget | None = None

    def do_startup(self) -> None:
        Gtk.Application.do_startup(self)
        provider = Gtk.CssProvider()
        families = f'"{self.options.font}", serif'
        provider.load_from_string(
            CSS.replace(
                ".quote-text {",
                f".quote-text {{ font-family: {families}; font-size: {self.options.size}px;",
            )
            .replace(".quote-author {", f".quote-author {{ font-family: {families}; font-size: 12px;")
            .replace(".quote-work {", f".quote-work {{ font-family: {families}; font-size: 12px;")
        )
        display = Gdk.Display.get_default()
        Gtk.StyleContext.add_provider_for_display(
            display, provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )

    def do_activate(self) -> None:
        self.widget = QuoteWidget(
            self, self.options, Deck(self.quotes, self.options.seed)
        )
        self.widget.present()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="quotes", description="A serif desktop widget for lines from classic books."
    )
    parser.add_argument("--print", dest="print_one", action="store_true",
                        help="print one quote and exit")
    parser.add_argument("--list", dest="list_authors", action="store_true",
                        help="list authors and quote counts, then exit")
    parser.add_argument("--data", help="path to quotes.json")
    parser.add_argument("--author", help="only use quotes from this author (substring)")
    parser.add_argument("--work", help="only use quotes from this book or essay (substring)")
    parser.add_argument("--seed", type=int, help="seed the shuffle (used for testing)")
    parser.add_argument("--interval", type=int, default=DEFAULT_INTERVAL,
                        help=f"seconds between quotes (default {DEFAULT_INTERVAL})")
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH,
                        help=f"card width in pixels (default {DEFAULT_WIDTH})")
    parser.add_argument("--margin", type=int, default=DEFAULT_MARGIN,
                        help=f"distance from the screen edge in pixels (default {DEFAULT_MARGIN})")
    parser.add_argument("--anchor", choices=sorted(CORNERS), default="bottom-right",
                        help="screen corner to sit in (default bottom-right)")
    parser.add_argument("--layer", choices=sorted(LAYERS), default="top",
                        help="Wayland layer to sit on (default top: visible over windows)")
    parser.add_argument("--font", default=DEFAULT_FONT,
                        help=f"serif family (default {DEFAULT_FONT!r})")
    parser.add_argument("--size", type=int, default=21, help="quote text size in pixels")
    parser.add_argument("--interactive", action="store_true",
                        help="take keyboard focus on demand so q/n/p work")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    options = parse_args(argv)
    data = Path(options.data) if options.data else None
    quotes = filter_quotes(load_corpus(data), options.author, options.work)

    if options.list_authors:
        counts: dict[str, int] = {}
        for quote in quotes:
            counts[quote["author"]] = counts.get(quote["author"], 0) + 1
        for author, count in sorted(counts.items()):
            print(f"{count:3d}  {author}")
        print(f"{len(quotes):3d}  total")
        return 0

    if options.print_one:
        print(describe(Deck(quotes, options.seed).take()))
        return 0

    app = QuotesApp(options, quotes)
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    return app.run([])


if __name__ == "__main__":
    sys.exit(main())

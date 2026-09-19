# quotes

## Purpose

Owns the widget itself: one GTK4 window placed by `gtk4-layer-shell` on a
Wayland layer, plus the command line interface that drives it.

## Ownership

`widget.py`. The flake installs it and wraps the interpreter.

## Local Contracts

- The module runs as a script. `flake.nix` calls `python3 <path>/widget.py`
  with the interpreter from `python3.withPackages (ps: [ ps.pygobject3 ])`.
- Defaults: layer `top`, anchor `bottom-right`, margin 44 px, width 470 px,
  interval 600 s, family `EB Garamond`, size 21 px. The `top` layer keeps the
  card above normal windows, which a tiling compositor would otherwise cover
  with a tile; `--layer bottom` gives the desktop-ornament behaviour instead.
- Left click shows the next quote. Right click prints the current quote and
  quits. Keys `n`, space and right arrow advance, and `q` and escape quit, but
  only with `--interactive`, because the surface otherwise takes no keyboard.
- Environment:
  - `QUOTES_DATA_DIR` - directory holding `quotes.json`. Set by the wrapper.
  - `QUOTES_FONT_FILE` - pathsep separated font files registered into Pango at
    startup, so the serif family resolves without a system font package.
- The corpus is read once at startup. `--print`, `--list`, `--author` and
  `--work` work without a display.

## Work Guidance

- `Gtk4LayerShell.init_for_window` MUST run before the window is presented, and
  every layer, anchor, margin, exclusive zone and keyboard mode setting comes
  with it.
- Keep the widget one window with one card. It sits over the wallpaper, so it
  keeps the transparent window background and paints only the card.
- Quote changes fade through `set_opacity` on a 16 ms GLib timeout. Keep the
  fade source id so a fast double click cannot leave two fades running.
- The deck reshuffles when it empties, so a quote never repeats back to back.

## Verification

- `nix run . -- --print --seed 7` prints a quote without a display.
- `nix run . -- --list` prints authors and counts.
- On a live Wayland session: `nix run .` and then `grim` a screenshot of the
  corner the widget occupies.

Proven on this host (mango, GTK 4.22, gtk4-layer-shell 1.3.0, 2026-09-19):

- The window maps as a layer surface: it never appears in `wlrctl toplevel list`.
- The card renders the quote, the author and the work on the live session.
- The typeface is the bundled EB Garamond, not a fallback. Two runs at the same
  seed produced byte-identical pixels, and `--font "No Such Serif At All"`
  changed 9.5% of the card pixels with a maximum channel delta of 215.

NOT proven on this host: the click and key handlers. `wlrctl` pointer clicks
reach no surface here, and the shell's own bar ignores them too, so injection is
broken rather than the handler. A bare GTK probe carrying GestureClick and
EventControllerMotion logged no event at all. Verify with a real pointer, or
with `xdotool` against a `GDK_BACKEND=x11` display.

## Child DOX Index

None.

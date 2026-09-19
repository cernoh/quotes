# quotes

An Android app and home screen widget that puts one line from a classic book on
your phone: Dostoevsky, Murakami, Mishima, Dazai, Kafka, Camus, Rilke, Marcus
Aurelius, Woolf, Baldwin and others. Lines about staying alive, keeping on, and
getting through the day.

The card is set in EB Garamond, which ships inside the APK. It shows the quote,
the author in letterspaced capitals, and the work in italics. Tap the quote for
the next one; tap the attribution to open the app.

The app has no internet permission. The corpus is compiled into the APK, so the
widget works with the phone offline.

## Build and install

```sh
nix-shell                      # Android SDK, build tools, emulator, Gradle, JDK 21
cd android
gradle assembleDebug \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-P` flag is required on NixOS. AGP downloads its own `aapt2` from Google
Maven and runs it as a daemon; that binary is built for a generic Linux and
cannot start here. The flag points AGP at the `aapt2` from the SDK. A line in
`local.properties` does not work: AGP reads that setting only from a Gradle
property.

`shell.nix` composes the SDK from nixpkgs. It needs
`android_sdk.accept_license = true`, which the file sets.

## The widget

- **Add it**: open the app and tap *Add widget*, or long-press the home screen,
  open *Widgets*, and pick *Quote of the moment*.
- **Tap the quote**: show the next quote.
- **Tap the author**: open the app.
- **Rotation**: every 15, 30, 60, 120 or 240 minutes. An inexact alarm drives
  it, so the system may delay an update a little to save power. Android does not
  fire repeating alarms more often than 15 minutes; a smaller number is clamped.

## Settings

| Setting | Effect |
| --- | --- |
| Rotate every | how long each quote stays |
| Only these authors | case-insensitive match on the author name, for example `Dostoevsky` |
| Only these works | case-insensitive match on the work title, for example `White Nights` |

A filter that matches nothing falls back to the whole corpus, so the widget can
never come up empty. *Apply* saves the settings and refreshes every placed
widget.

## The corpus

`data/quotes.json` is the single source of truth:

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
from memory, and no quote is paraphrased. `work` names the book, story or essay,
and is `null` when Wikiquote gives none.

Three checks hold that rule:

```sh
nix run nixpkgs#python3 -- tools/check-corpus.py      # shape, duplicates, counts
nix run nixpkgs#python3 -- tools/verify-grounding.py  # fetches every source page
cd android && gradle copyCorpus                       # the build-time gate
```

The Gradle task `copyCorpus` copies the corpus into the APK assets and fails the
build when a quote misses a key, when a text falls outside 40 to 400 characters,
when a source is not a Wikiquote URL, or when the corpus drops below 40 quotes.

## Layout

```
data/quotes.json                  the corpus
tools/check-corpus.py             offline validation
tools/verify-grounding.py         proves each quote is on its source page
android/                          the app and the widget
shell.nix                         the Android toolchain from nixpkgs
```

The typeface is EB Garamond by Georg Duffner and Octavio Pardo, used under the
SIL Open Font License 1.1.

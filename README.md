# quotes

An Android app and home screen widget that puts one line from a classic book on
your phone: Dostoevsky, Murakami, Mishima, Dazai, Kafka, Camus, Rilke, Marcus
Aurelius, Woolf, Baldwin and others. Lines about staying alive, keeping on, and
getting through the day.

The card is set in EB Garamond, which ships inside the APK. It shows the quote,
the author in letterspaced capitals, and the work in italics. Tap the quote for
the next one; tap the attribution to open the app.

<p>
  <img src="docs/screenshots/app.png" width="250" alt="The app showing a quote by Osamu Dazai from No Longer Human, with buttons for the next quote, for adding the widget, and for the settings">
  <img src="docs/screenshots/settings.png" width="250" alt="The settings page: rotation, sources, card, order, and updates">
  <img src="docs/screenshots/home.png" width="250" alt="The same card as a widget on the Android home screen">
</p>

The app asks for the internet permission for one thing only: the update check.
Nothing else uses the network, no check runs in the background, and the corpus is
compiled into the APK, so the widget works with the phone offline.

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

## Release signing

A debug APK can never update another debug APK: every machine generates its own
debug key, so Android refuses with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Real releases use one key that is kept.

1. Generate the key once, and keep it outside the repository:

```sh
keytool -genkeypair -v -keystore ~/.android-keys/quotes-release.jks \
  -alias quotes -keyalg RSA -keysize 4096 -validity 10000
```

2. Copy `android/keystore.properties.example` to `android/keystore.properties`
   and fill it in. That file is gitignored.
3. Build and check the signature:

```sh
cd android
gradle assembleRelease bundleRelease \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

**Back the keystore up.** Android accepts an update only from the same key. Lose
it and the app can never be updated again; every user must uninstall first.

Without `android/keystore.properties`, `assembleRelease` still builds and writes
`app-release-unsigned.apk`, which is what F-Droid wants.

## Not on F-Droid

F-Droid's inclusion policy requires that all assets be free or public domain and
that the app not infringe third-party copyright. The corpus bundles quotes from
16 authors whose works or English translations are in copyright, so the app
cannot ship there without dropping most of the authors it exists for. The app
updates itself from the GitHub releases instead, and you can add this repository
to the F-Droid client if you build and sign it yourself.

An F-Droid build and a sideloaded build can never replace each other, because
the signatures differ. Choose one route per device.

## The widget

- **Add it**: open the app and tap *Add widget*, or long-press the home screen,
  open *Widgets*, and pick *Quote of the moment*.
- **Size**: the card adapts to the cell you give it. A small cell drops to
  smaller text, fewer lines, and no rule, so the author stays on the card. A tall
  cell takes larger text and more lines. A quote too long for the cell is cut
  with an ellipsis.
- **Tap the quote**: show the next quote.
- **Tap the author**: open the app.
- **Rotation**: every 15, 30, 60, 120 or 240 minutes, or never. An inexact alarm
  drives it, so the system may delay an update a little to save power. Android
  does not fire repeating alarms more often than 15 minutes; a smaller number is
  clamped.

## Settings

Every change applies at once, to the card in the app and to every placed widget.
There is no Apply button.

| Setting | Effect |
| --- | --- |
| Rotate every | `Never, only when I tap`, or 15, 30, 60, 120 or 240 minutes |
| Authors | pick the authors to draw from. Empty means all of them |
| Works | pick the works to draw from. Empty means all of them |
| Text size | 80 to 140 percent, on top of the size the widget was given |
| Show the work title | hide the work line for a plainer card |
| Light card | a cream card with dark text, for a light home screen |
| Shuffle the order | off walks the corpus in order |
| Install updates without asking | installs an update through Shizuku, with no prompt. Turn it on only with Shizuku running and the permission granted |

A selection that leaves nothing falls back to the whole corpus, so the widget can
never come up empty.

## Updates

The app installs from this repository, not from a store, so it updates itself
from the GitHub releases:

1. Open *Settings* and find the *Updates* section. It names the installed
   version.
2. Tap *Check for updates*. The app asks GitHub for the latest release and
   compares the two versions.
3. If a later release exists, tap *Download and install*. The APK lands in the
   app cache, and the system installer asks for your confirmation.

The repository is public, so the check needs no token: the anonymous release
request answers with the latest release. The *GitHub token* field exists for the
case where the repository returns to private, since the API then answers `404` to
an anonymous request and the field is the only way in. The token stays on the
phone.

An interrupted update stays in the cache. The settings then offer *Install the
downloaded update*, so a download is never wasted.

### Without a prompt, through Shizuku

[Shizuku](https://shizuku.rikka.app/) lets an app run a command as the shell
user. An update then installs with no confirmation screen:

1. Install Shizuku, then start its service. Shizuku can start from root, or from
   the command it shows for a computer.
2. Open the settings and find *Automatic install*. The line under it names the
   state of Shizuku.
3. Tap *Grant Shizuku permission* and allow it.
4. Turn on *Install updates without asking*.

The app then runs `pm install -r -S <size>` as the shell user and streams the APK
into it, so the file never needs to be readable by anyone else.

Shizuku is optional. Without it, the system installer handles every update, and
the app behaves as it did before.

The APK is signed with the project release key. Android accepts an update only
from the same key, so keep `~/.android-keys/quotes-release.jks` safe. A device
that carries an older debug-signed build must uninstall it once before the first
release-signed install, because debug signatures never match a release
signature.

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

The app is MIT licensed; see `LICENSE`. The typeface is EB Garamond by Georg
Duffner and Octavio Pardo, used under the SIL Open Font License 1.1. Quotes come
from English Wikiquote, which publishes under CC BY-SA 4.0.

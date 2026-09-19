# android

## Purpose

Owns the Android app and the home screen widget: the card layout, the widget
provider, the rotation timer, the settings, and the corpus wiring.

## Ownership

Gradle project under `android/`, Kotlin sources under
`app/src/main/java/dev/cernoh/quotes/`, resources under `app/src/main/res/`.
`shell.nix` at the repository root provides the toolchain.

## Local Contracts

- Framework APIs only. No androidx, so the build needs nothing beyond the SDK
  and Kotlin. Adding a dependency is a decision, not a detail.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`, Kotlin 2.2.20, AGP 9.2.1,
  Gradle 9.7.1, JDK 21.
- `app/src/main/assets/quotes.json` is generated. The `copyCorpus` task writes it
  from `../data/quotes.json` and fails the build on a missing key, a text
  outside 40 to 400 characters, a non-Wikiquote `source`, or fewer than 40
  quotes. Never edit the asset by hand and never commit it.
- `R.layout.widget_card` is the single card. `MainActivity` inflates the same
  layout, so the preview in the app is the widget, not a copy of it.
- The widget MUST size itself to the cell it is given. `WidgetSizing` turns the
  placed size into a text size, a line limit, and a padding, and
  `WidgetRenderer.applyTier` applies them on every render, including after
  `onAppWidgetOptionsChanged`.
- Read the current size from the option pair for the orientation. Measured on an
  emulator, a 3 by 2 placement reports minimum 224x136dp and maximum 434x284dp,
  and the widget draws 224 wide by 284 high. The current size is therefore the
  minimum width and the maximum height in portrait, and the reverse in landscape.
  Reading the minimum height instead sized the card for a cell it did not have.
- The line limit MUST come from the height, not from the class alone: a class
  whose lines do not fit drops to a smaller class, or to fewer lines. Without
  that, a long quote pushes the author off the card.
- `widget_card.xml` keeps the largest sizes as its defaults, because the activity
  inflates the same layout. The widget overrides them per placement.
- Every render logs one line: `widget <id> is <w>x<h>dp: <size>sp, maxLines <n>,
  padding <n>dp`. Use `adb logcat -s quotes:I` to see what the launcher reported.
- Settings live in `SettingsActivity`, one section each: rotation, sources,
  card, order, and updates. Every change writes to `Prefs` and calls
  `WidgetRenderer.updateAll` at once, so there is no Apply button. `MainActivity`
  keeps only the card, the next-quote button, the add-widget button, and the
  button that opens the settings.
- Source selections are multi-choice lists built from the corpus, not free text.
  An empty selection means the whole corpus, and a selection that matches nothing
  falls back to the whole corpus.
- `Updates` holds the whole update path: read the latest GitHub release, compare
  versions, download the APK, and hand it to the system installer through
  `PackageInstaller`. Never install anything directly, and never run the check in
  the background: the user taps a button or nothing happens.
- A private repository answers `404` to an anonymous release request, so
  `Updates.check` reports a missing token rather than a network fault on 404.
  Keep that distinction: it is the difference between "add a token" and "your
  network is down".
- The two permissions in the manifest have one purpose each: `INTERNET` for the
  update check, `REQUEST_INSTALL_PACKAGES` for the installer handover. Adding a
  third needs a reason in this file.
- `MainActivity` and `SettingsActivity` MUST call `WindowSpacing.apply` on their
  root view. The app draws edge to edge, so a screen that skips it puts its
  content under the status bar and the camera cutout.
- The corpus is shuffled from a stored seed rather than stored as a list, so a
  widget redraw after a reboot shows the same order.
- Actions: `dev.cernoh.quotes.action.NEXT` from a tap,
  `dev.cernoh.quotes.action.ROTATE` from the alarm. Both land in
  `QuotesWidgetProvider.onReceive`.

## Work Guidance

- The launcher may ask for an update at any time; `onUpdate` MUST redraw from
  `Prefs.current` and MUST NOT advance the quote, or a redraw would skip lines.
- Advancing happens only in `onReceive`, for `NEXT` and `ROTATE`.
- Keep the alarm inexact. An exact repeating alarm needs a permission and wakes
  the device for a decoration.
- The card is fixed dark. It sits over wallpaper, so it must not depend on the
  system light or dark theme.

## Verification

Build and install on an emulator, then check both surfaces:

```sh
nix-shell
cd android && gradle assembleDebug \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.cernoh.quotes/.MainActivity
adb exec-out screencap -p > /tmp/app.png
```

- The app screen MUST show the quote in EB Garamond with the author and the work.
- Pin the widget from the app, then capture the home screen and check the same
  card there.
- Tap coordinates: read them from `uiautomator dump`, never from a scaled
  screenshot. `screencap` writes real pixels, and a vision read of the PNG
  usually reports the displayed size instead.
- `gradle copyCorpus` MUST fail when `data/quotes.json` breaks a rule. Break one
  rule on purpose when the task changes.
- `gradle test` covers the size classes and the update logic. The live update
  call cannot be tested without a token for the private repository: the anonymous
  path returns the 404 message, which is the state to expect on a fresh install.
- A setting MUST reach the placed widget in one tap. Toggle *Light card* and
  check the home screen, not only the preview in the app.

## Child DOX Index

None.

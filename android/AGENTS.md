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
- A plain `View` is not a RemoteViews view. The rule under the quote is a
  `TextView` with a background colour for that reason.
- `PreferenceManager` is not used. `Prefs` holds the rotation position, the
  shuffle seed, the interval and the filters.
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

## Child DOX Index

None.

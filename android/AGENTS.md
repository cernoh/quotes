# android

## Purpose

Owns the Android app and the home screen widget: the card layout, the widget
provider, the rotation timer, the settings, and the corpus wiring.

## Ownership

Gradle project under `android/`, Kotlin sources under
`app/src/main/java/dev/cernoh/quotes/`, resources under `app/src/main/res/`.
`shell.nix` at the repository root provides the toolchain.

## Local Contracts

- The build carries one dependency and one exception to the framework-only rule:
  the Shizuku API and provider, pinned to 12.2.0, which bring
  `androidx.annotation` with them. Nothing else from androidx is allowed, and
  every other dependency is a decision to record in this file.
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
  `WidgetRenderer.updateAll` at once, so there is no Apply button.
  `MainActivity` keeps only the card, the next-quote button, the add-widget
  button, and the button that opens the settings.
- Source selections are multi-choice lists built from the corpus, not free text.
  An empty selection means the whole corpus, and a selection that matches nothing
  falls back to the whole corpus.
- `Updates` holds the whole update path: read the latest GitHub release, compare
  versions, download the APK, then hand it to the installer. An install reaches
  `PackageInstaller`, which shows the system screen, or Shizuku, which installs
  without one. Shizuku MUST stay an explicit opt-in: nothing installs without
  either the system confirmation or the switch the user turned on. The check
  never runs in the background: the user taps a button or nothing happens.
- A private repository answers `404` to an anonymous release request, so
  `Updates.check` reports a missing token rather than a network fault on 404.
  Keep that distinction: it is the difference between "add a token" and "your
  network is down".
- The manifest declares two permissions, one purpose each: `INTERNET` for the
  update check, and `REQUEST_INSTALL_PACKAGES` for the installer handover. The
  Shizuku provider declares `INTERACT_ACROSS_USERS_FULL` as well, which is how
  Shizuku hands its binder to this app; it belongs to the provider, not to the
  app. A third app permission needs a reason in this file.
- The Shizuku dependency is pinned to 12.2.0 on purpose. `Shizuku.newProcess` is
  public there and private from 13.0, and the streamed `pm install` needs it.
  Moving to 13.x means rewriting the install as a `bindUserService` service.
  The API also brings `androidx.annotation`, the one androidx artifact in the
  build; nothing else from androidx is allowed.
- Shizuku is opt-in and MUST stay opt-in. `Prefs.shizukuInstall` decides, the
  switch refuses to turn on unless the state is READY, and every path falls back
  to `Updates.install`, the system installer.
- `ShizukuInstaller.state(context)` MUST decide presence from the installed
  package, not from an exception: `Shizuku.pingBinder()` answers `false` instead
  of throwing when nothing runs, so a user without Shizuku would read the wrong
  message.
- The install streams the APK on standard input (`pm install -r -S <size>`), so
  the app-private cache file never has to be readable by the shell user.
- `PackageInstaller.Session.commit` needs a **mutable** `PendingIntent` on
  Android 14 and later. An immutable one throws
  `IllegalArgumentException: The commit() status receiver should come from a
  mutable PendingIntent`, and the whole app dies. Found this way on
  2026-09-19; `assembleDebug` and lint do not catch it.
- The system installer path is NOT yet confirmed on an emulator. One run reached
  `session.commit()` without a crash, no installer screen appeared, and the
  installed version did not change. Logcat showed `markAsSealed` logging
  `ServiceNotFoundException: No service published for: persistent_data_block`.
  That log line is the only evidence so far: whether the session sealed, whether
  `UpdateResultReceiver` fired, and why no screen appeared are all unknown. Check
  `dumpsys package installer` and the receiver before blaming the image.
- `MainActivity` and `SettingsActivity` MUST call `WindowSpacing.apply` on their
  root view. The app draws edge to edge, so a screen that skips it puts its
  content under the status bar and the camera cutout.
- The corpus is shuffled from a stored seed rather than stored as a list, so a
  widget redraw after a reboot shows the same order.
- Actions: `dev.cernoh.quotes.action.NEXT` from a tap,
  `dev.cernoh.quotes.action.ROTATE` from the alarm. Both land in
  `QuotesWidgetProvider.onReceive`.

## Styling

- The palette is the desktop sepia scheme, so the phone and the desktop read as
  one print: `base #1e1813` for the screen, `surface #241d17` for the card,
  `text #ece0cd`, `primary #c99a5b` for the accent, `textDim #9c8c74`,
  `outline #5f4d3a` for the card edge. Change them in `res/values/colors.xml`
  only.
- A few numbers come from a design seed, rolled from `/dev/urandom` as `62314`:
  the card radius is `12 + seed % 3 * 2 = 14dp`, the card alpha is
  `0.72 + 0.06 * (seed % 3) = 0.78`, and the author letterspacing is
  `0.08 + 0.02 * (seed % 3) = 0.10em`. The rule width and the spacing scale
  follow the same idea: `res/values/dimens.xml`. Re-roll the seed and recompute
  those numbers, rather than editing them by feel.
- Keep it minimal: two typefaces (EB Garamond for the card and the section
  headings, the system face for controls), flat buttons with no slab, one
  accent, and no shadows beyond the card.
- The widget card and the app preview share `widget_card.xml` and the same
  drawables, so a colour change reaches both.
- The card colour switch in the settings picks the light drawable and the
  `*_light` colours. Add a colour to both sets, or the light card breaks.

## Work Guidance

- The launcher may ask for an update at any time; `onUpdate` MUST redraw from
  `Prefs.current` and MUST NOT advance the quote, or a redraw would skip lines.
- Advancing happens only in `onReceive`, for `NEXT` and `ROTATE`.
- Keep the alarm inexact. An exact repeating alarm needs a permission and wakes
  the device for a decoration.
- The card MUST NOT follow the system light or dark theme. Its colour is the
  user's setting, because it sits over wallpaper.

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
- `gradle test` covers the size classes and the update logic. The repository is
  public since 2026-09-19, so `releases/latest` answers `200` to an anonymous
  request and the positive update path is testable without a token. The token
  field matters again only if the repository returns to private, where the API
  answers `404`.
- A setting MUST reach the placed widget in one tap. Toggle *Light card* and
  check the home screen, not only the preview in the app.

## Child DOX Index

None.

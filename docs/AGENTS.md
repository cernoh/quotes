# docs

## Purpose

Owns the images that the top-level `README.md` shows.

## Ownership

`docs/screenshots/`. The README links them; nothing else does.

## Local Contracts

- `screenshots/app.png` shows the app screen: the card, the next-quote button,
  the add-widget button, the settings button, and the grounding line.
- `screenshots/home.png` shows the home screen with the widget in place.
- `screenshots/settings.png` shows the settings page.
- All are 420 pixels wide, so they sit side by side in the README.
- An image MUST come from a real run of the app on a device or an emulator. A
  mock, a design tool drawing, or a screenshot of an older release is not
  allowed here.
- Capture on an emulator dedicated to this project. Its home screen MUST carry
  nothing but the stock launcher and this widget, so no other project appears in
  the image. Check with `adb -s <serial> shell pm list packages -3`: the only
  third-party package MUST be `dev.cernoh.quotes`.

## Work Guidance

Recapture after a change to the card:

```sh
adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n dev.cernoh.quotes/.MainActivity
adb -s emulator-5554 exec-out screencap -p > /tmp/app.png
# pin the widget from the app, go home, then capture again
adb -s emulator-5554 exec-out screencap -p > /tmp/home.png
ffmpeg -y -i /tmp/app.png -vf "crop=1080:1000:0:0,scale=420:-1" docs/screenshots/app.png
ffmpeg -y -i /tmp/home.png -vf "scale=420:-1" docs/screenshots/home.png
```

The crop keeps the card and the buttons of the app screen and drops the lower
half of the settings form. Keep the scale at 420 pixels wide.

## Verification

Open both files and check three things: the quote is set in the serif face, the
author line is present, and the attribution names a work. A capture that lost the
author line means the card layout regressed.

## Child DOX Index

None.

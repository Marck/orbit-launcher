# Orbit launcher

A replacement home screen for an Android TV set: one screen, no advertising rows,
a hero for what you were last watching, a scrolling row of favourite apps, and a row
of TV inputs.

Status: design and requirements only. No application code yet.

## Contents

| File | What it is |
|---|---|
| `index.html` | The chosen design, Orbit, with its live-TV variant, uninstall feedback, guide fallback ladder, scale spec and the add-to-favourites picker |
| `alternatives.html` | Three directions that were considered and rejected |
| `bg-hero.jpg` | Generated placeholder backdrop for the mockups |

Open either HTML file in a browser. They are static, with no build step and no
external requests.

## Notes on content

The mockups contain no real artwork: `bg-hero.jpg` is generated from a gradient.
App logos are simplified shapes drawn for layout only; no official brand assets are
included, and all marks belong to their respective owners.

No playlist URLs, guide URLs, credentials, addresses or device identifiers appear in
this repository, and none will be added. Those are configured on the device at runtime
and stay there.

## Licence

MIT. See `LICENSE`.

## Building

Needs JDK 17 and the Android SDK (platform 35, build-tools 35).

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI builds the same artifact, but only on manual `workflow_dispatch`.

## Favourites and order

Each row holds only your favourites, in your order, then a `+` tile. With no
favourites chosen, the row holds only the `+`. It opens a grid of everything
available, with favourites ticked; select one to add or remove it. Favourites are
stored as package names, so they survive an update or a reinstall.

Hold OK on a tile, or press Menu, to open its menu:

- **Open** does what OK does.
- **Move** starts a move: left and right change its place, OK keeps it, Back puts
  the order back.
- **Remove from favourites** takes it out of the row. It stays installed.
- **Uninstall**, for apps the system allows it on, hands over to the system, which
  does the confirming. An app removed anywhere drops out of favourites by itself.

At either end of a row or grid, left and right wrap around, so the far end is one
press away rather than a long walk.

## Top bar

Left of the profile picture sit three small buttons: the TV's own inputs panel, the
Play Store and Settings. Press up from the rows to reach them. A button is left out
when the TV has nothing for it to open.

## Setting the profile picture

Select the avatar in the top bar. The picker offers Orbit's own set, which is drawn
in code and always available, and any pictures on the TV. A chosen picture is copied
into the app's own storage, cropped to a circle, and never leaves the TV.

The signed-in account's picture cannot be used: no provider on an Android TV exposes
it to a third-party app.

To use your own picture, put one on the TV first, by downloading it with the TV's web
browser, by plugging in a USB stick, or over adb:

```bash
adb push your-picture.png /sdcard/Download/
```

Listing them needs the pictures permission, which the picker asks for. A file copied
in over adb is not indexed right away, so it may take reopening the picker once.

## Waking the TV for testing

With the panel off, screenshots come back blank and the UI never redraws, because
there are no frames to drive it. Check and wake before testing anything visual:

```bash
adb shell dumpsys power | grep "Display Power"   # state=ON or OFF
adb shell input keyevent 224                     # WAKEUP
```

# Orbit launcher

A replacement home screen for an Android TV set: one screen, no advertising rows,
a hero for what you were last watching, a scrolling row of favorite apps, and a row
of TV inputs.

Status: design and requirements only. No application code yet.

## Contents

| File | What it is |
|---|---|
| `index.html` | The chosen design, Orbit, with its live-TV variant, uninstall feedback, guide fallback ladder, scale spec and the add-to-favorites picker |
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

CI builds the same artifact, runs Android lint and the unit tests on every pull request
and every push to `main`; it can also be started by hand. New lint errors fail the build;
the ones already there are listed in `app/lint-baseline.xml`.

## The hero

The top of the screen shows the most truthful thing Orbit can say, in this order:

1. **Continue watching.** What you were actually watching, read from the TV's own Watch
   Next list, which apps such as Jellyfin publish to. Title, episode and OK to resume,
   with the programme's own artwork bled in from the right and faded out across the
   middle and along the bottom, so it carries on behind the rows. Where there is more
   than one thing to carry on with, the hero steps through them every ten seconds. A film
   shows no episode line.

   A bar, with how much is watched and how much is left, appears only when the publishing
   app reported both a position and a length. Most rows are a next episode rather than a
   part-watched one and carry neither, so most of the time there is no bar rather than an
   invented one. The two numbers are minutes for anything that plays; for a kind whose
   unit is not time the bar says a percentage instead, because the API gives no unit.
   Android TV has no book type at all, so "Page x of y" is not something an app can tell
   Orbit through this list.
2. **Back to the last app or input** Orbit opened, for up to twelve hours. This needs no
   permission: it is Orbit's own record, and it covers only what was opened from Orbit.
3. **The time and date,** when there is nothing to offer.

Reading another app's rows needs `READ_TV_LISTINGS`, a normal permission prompt. Orbit
asks for it once, from the hero, never as a popup on start, and the hero works without
it. Nothing read there leaves the TV: the artwork is opened straight from the publishing
app through the content resolver, and those URIs are never logged, because an app's own
content URI can carry your server address in it.

A row is data from another app, so the intent on it is not followed blindly. A component
has to belong to the app that published the row, anything without one is addressed to
that app by name, and any selector is dropped.

## Focus

The tile in focus grows a little and its label turns white. There is no ring around it:
the pop-out is the whole signal. A tile being moved is the one exception, and it gets an
amber ring for as long as the move lasts.

## Favorites and order

Each row holds only your favorites, in your order, then a `+` tile. With no
favorites chosen, the row holds only the `+`. It opens a grid of everything
available, with favorites ticked; select one to add or remove it. Favorites are
stored as package names, so they survive an update or a reinstall.

Hold OK on a tile, or press Menu, to open its menu:

- **Open** does what OK does.
- **Move** starts a move: left and right change its place, OK keeps it, Back puts
  the order back.
- **Remove from favorites** takes it out of the row. It stays installed.
- **Uninstall**, for apps the system allows it on, hands over to the system, which
  does the confirming. An app removed anywhere drops out of favorites by itself.

At either end of a row or grid, left and right wrap around, so the far end is one
press away rather than a long walk.

## Back, Home and coming back

Up and down between the rows and the top bar land on the tile you left, not on
whichever one happens to be nearest. Coming back from an app, an input, the TV's
settings or a picker puts focus back where it was, in the row or the top bar.

Back closes an open menu or puts back a move. Otherwise it goes to the first app,
rather than closing Orbit. Home pressed while Orbit is showing does the same, and
also closes the menu and puts back a move that was not kept.

## Top bar

Left of the profile picture sit four small buttons: All apps, the TV's own inputs panel,
the Play Store and Settings. Press up from the rows to reach them. A button is left out
when the TV has nothing for it to open.

**All apps** opens the same grid as the add tile, but selecting an app there opens it.
Holding OK, or pressing Menu, adds it to the favorites or takes it out again. The rows
show favorites only, so this is how an app that is not a favorite gets opened.

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

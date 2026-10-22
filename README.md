# Orbit launcher

A replacement home screen for an Android TV set: one screen, no advertising rows,
a hero for what you were last watching, a scrolling row of favorite apps, and a row
of TV inputs.

Status: in use on the owner's set. Favorites, reordering, the input row, the pickers,
the profile picture and the hero all work.

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
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`assembleDebug` builds the same app unshrunk. Use that one when you need
`adb shell run-as`, and the release build for anything judged by feel: Compose on a TV
chip is noticeably smoother shrunk, which shows in the focus pop-out and the scrolling
labels.

CI builds the release APK, runs Android lint and the unit tests on every pull request and
every push to `main`; it can also be started by hand. New lint errors fail the build; the
ones already there are listed in `app/lint-baseline.xml`. Each run gets its own
`versionCode` from `ORBIT_VERSION_CODE`, so every CI build installs over the one before
it. A build here keeps `versionCode` 1, so pass the variable to install over a CI build.

### Signing

`app/debug.jks` is committed, and both build types use it. The point is not secrecy: a debug
key is worth nothing as proof of origin, and anyone can sign an APK with this one. The point
is that every build, here and in CI, is signed with the *same* key, so a new APK installs over
the last instead of forcing an uninstall that wipes the favorites, the avatar and the
accessibility setting.

To sign with a real key instead, point `keystore.properties` at it:

```bash
cat > keystore.properties <<EOF
storeFile=/path/to/your.jks
storePassword=<the store password>
keyAlias=<the alias>
keyPassword=<the key password>
EOF
```

Git ignores `keystore.properties` and every `*.jks` but the committed one.
`ORBIT_KEYSTORE_PROPERTIES` points at the file if it lives elsewhere; CI reads the same four
values from `ORBIT_STORE_FILE`, `ORBIT_STORE_PASSWORD`, `ORBIT_KEY_ALIAS` and
`ORBIT_KEY_PASSWORD`. Switching keys needs one uninstall.

Checking which key signed an APK needs `apksigner`, because with `minSdk 29` the APK carries
no v1 JAR signature for `keytool` to read:

```bash
"$ANDROID_HOME"/build-tools/35.0.0/apksigner verify --print-certs app-release.apk
```

## The hero

The top of the screen shows the most truthful thing Orbit can say, in this order:

1. **Continue watching.** What you were actually watching, read from the TV's own Watch
   Next list, which apps such as Jellyfin publish to. Title, episode and OK to resume,
   with the programme's own artwork bled in from the right and faded out across the
   middle and along the bottom, so it carries on behind the rows. Where there is more
   than one thing to carry on with, the hero steps through them every ten seconds. A film
   shows no episode line. The hero follows the app under the cursor, so a programme never
   appears behind a tile that knows nothing about it.

   An app that serves nothing gets an ambient picture instead, named in the hero after the
   app that published it, taken from the rows apps publish to the TV's own preview list.
   Only the media server publishes those as content URIs; every other app publishes an
   https link to its own artwork, which is the one thing Orbit uses the network for. It
   asks for the picture and sends nothing. A picture that will not load is not offered at
   all, so a title never appears over a black rectangle. Those rows carry no genre and
   no age rating on this set, so there is nothing to pick by but the app, and holding OK
   on the hero chooses which apps supply them. Anything already in Watch Next is left out,
   so an ambient picture never repeats what the hero offers elsewhere.

   A picture that says what it is comes first. Of the 1359 rows on one set, 162 carry a
   description and 1197 carry nothing but a name, almost all of them from a single shop
   front, so left shuffled together seven backdrops in eight would be a bare title. The
   ones with nothing to say are kept on the end rather than dropped, so an app chosen on
   its own still supplies something. Title, description, the detail line and the bar are
   one block of layout, so a Watch Next row and an ambient picture read the same.

   A bar, with how much is watched and how much is left, from the first of two sources
   that has an answer:

   - the row's own position, when the publishing app put one there. Most rows are a next
     episode rather than a part-watched one and carry none;
   - what the TV was last seen playing, read from the playing app's own media session.
     That needs Orbit turned on under notification access, which the hero offers when a
     programme is on screen with no bar. Nothing is ever read from a notification: the
     listener exists only because it is the one door to the media sessions.

   With neither, there is no bar rather than an invented one. The two numbers are minutes
   for anything that plays; for a kind whose unit is not time the bar says a percentage
   instead, because the API gives no unit. Android TV has no book type at all, so "Page x
   of y" is not something an app can tell Orbit through this list.
2. **The way back,** when there is neither something to carry on with nor a picture to
   show for it: "Back to Kodi", and OK opens it again. It is Orbit's own record of what it
   last opened, kept for twelve hours, so it needs no permission. It can only name what
   Orbit itself opened: an app reached with the remote's own keys is not in it and is not
   claimed to be. It comes last of the three because it is a way back rather than
   something to watch.
3. **The time and date,** when there is nothing at all.

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

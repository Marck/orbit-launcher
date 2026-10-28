# Orbit launcher

A replacement home screen for an Android TV set: one screen, no advertising rows,
a hero for what you were last watching, a scrolling row of favorite apps, and a row
of TV inputs.

Status: in use on the author's set. Favorites, reordering, the input row, the pickers,
the profile picture, the hero, the per-tile menu with uninstall, and the About card all
work.

It was written for one Android TV set and nothing about it is specific to that set beyond
what is written down here. It needs no account, no server and no network service of its
own, and it sends nothing anywhere.

## Putting Orbit on your TV

Four steps. The whole thing takes about ten minutes, and every one of them is reversible.

**1. Build and install it.** You need a JDK (17 or newer) and the Android SDK. The releases
on GitHub are tags and notes only, with no APK attached, so you build it yourself:

```bash
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

adb connect <your-tv-ip>:5555          # or use a USB cable
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Enable developer options and USB or network debugging on the TV first: Settings, Device
Preferences, About, then press Build seven times. See [Signing](#signing) before you build,
because the key you sign with decides whether you can ever update without losing your
settings.

**2. Open it once and give it the listings permission.** Orbit asks the first time it needs
it, from the hero. Without it Orbit still works, it just has nothing to put in the hero or
the poster row, because those are other apps' rows and reading them needs
`READ_TV_LISTINGS`. Nothing leaves the TV either way.

**3. Make it your home screen.** See [Making Orbit the home screen](#making-orbit-the-home-screen).
Expect this to be the fiddly step: on at least one maker's set the stock launcher cannot be
outvoted and has to be disabled instead. There is a documented way back.

**4. Choose what is on it.** Everything on the home screen is yours to pick:

| What | How |
|---|---|
| Favorite apps | The grid glyph in the top bar, or the `+` tile at the end of the apps row |
| Reorder a tile | Focus it, hold OK, then left and right. OK keeps it, Back puts it back |
| Remove a tile | Focus it, hold OK, then Remove from favorites |
| Uninstall an app | Focus it, hold OK, then Uninstall. Works from the apps row and from All apps |
| TV inputs | The `+` tile at the end of the inputs row |
| Which apps may fill the hero | The picture glyph in the top bar |
| Profile picture | The avatar glyph in the top bar |

Nothing is configured in a file, and nothing you pick is stored anywhere but the TV.

### If you want to undo all of it

```bash
adb shell pm enable com.google.android.tvlauncher   # if you disabled it
adb uninstall app.orbit.launcher
```

The stock launcher comes straight back. Uninstalling also takes Orbit's settings and its
permission with it.

## The mockups

| File | What it is |
|---|---|
| `index.html` | The chosen design, with its live-TV variant, uninstall feedback, guide fallback ladder, scale spec and the add-to-favorites picker |
| `alternatives.html` | Three directions that were considered and rejected |
| `bg-hero.jpg` | Generated placeholder backdrop for the mockups |

Static, no build step and no external requests. They are the design the app was built from,
kept because they are still the quickest way to see what was intended.

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

Needs a JDK (17 or newer; the build targets JVM 17 bytecode) and the Android SDK.
`compileSdk` is 37, `targetSdk` 35 and `minSdk` 29, so the oldest set this runs on is
Android 10.

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
it. A build here derives `versionCode` from `orbitVersion` instead, so 0.3.1 is 301; pass
the variable if you need a local build to install over a CI build with a higher number.

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
- **Uninstall** hands over to the system, which does the confirming. An app removed
  anywhere drops out of favorites by itself. On a factory app that has since updated
  itself the entry reads **Uninstall updates**, because that is what the system will
  actually do: the update comes off and the app stays.
- **Manage** takes the place of Uninstall on a factory app that never updated, which
  the system will not remove at all. It opens the TV's own page for the app, where it
  can be turned off instead.

At either end of a row or grid, left and right wrap around, so the far end is one
press away rather than a long walk. Only on a press of its own: hold the key to run
along the row and it stops at the end instead of carrying past it, so the last tile
can be aimed at. Let go and press again to come round.

## Back, Home and coming back

Up and down between the rows and the top bar land on the tile you left, not on
whichever one happens to be nearest. Coming back from an app, an input, the TV's
settings or a picker puts focus back where it was, in the row or the top bar.

Back closes an open menu or puts back a move. Otherwise it goes to the first app,
rather than closing Orbit. Home pressed while Orbit is showing does the same, and
also closes the menu and puts back a move that was not kept.

## Making Orbit the home screen

Two separate things, and the second is the one that matters on this set.

**1. The preferred home activity, which on this set does nothing.** Orbit declares
`android.intent.category.HOME`, so it is a candidate as soon as it is installed. Setting it as
the preferred one reports success and changes nothing:

```bash
adb shell cmd package set-home-activity app.orbit.launcher/.HomeActivity
adb shell cmd package resolve-activity -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

The TV maker's launcher comes back, because it declares `mPriority=2` on its own home filter and
no preferred activity is recorded at all:

```bash
adb shell dumpsys package com.google.android.tvlauncher | grep -A 4 'category.HOME'
```

So there are two ways to actually get there, and they are independent.

**2. Disable the stock launcher.** The direct one. With it gone, the home key has nowhere else
to resolve, and Orbit opens with no flash of anything else first:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package resolve-activity -a android.intent.action.MAIN \
  -c android.intent.category.HOME        # now app.orbit.launcher.HomeActivity
```

This is reversible and it is not a one-way door. `com.android.tv.settings.system.FallbackHome` is
part of the system, stays enabled and is direct-boot aware, so the TV always has a home screen to
fall back on even if Orbit will not start. To put the stock launcher back:

```bash
adb shell pm enable com.google.android.tvlauncher
```

**3. The home button service.** The way round for leaving the stock launcher enabled. It is an
accessibility service that watches for the stock home screen coming to the front and puts Orbit
there instead, so the home key lands on Orbit about half a second later. Measured on the set:
521ms. Not needed once the stock launcher is disabled, and harmless to leave on.

On the TV: Settings, Device Preferences, Accessibility, then Orbit home button. Orbit offers
this itself, in the notice under the hero, which opens the home settings and falls back to the
accessibility settings on a set that has no home setting. Over adb:

```bash
adb shell settings put secure enabled_accessibility_services \
  app.orbit.launcher/app.orbit.launcher.HomeButtonService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys accessibility | grep -i orbit   # Bound services: should name Orbit
```

Then press home from inside any app and check where it lands:

```bash
adb shell dumpsys activity activities | grep mResumedActivity
```

Both settings persist across a reboot. Neither survives an uninstall, which is why both build
types are signed with the same key: see [Signing](#signing).

**Force-stopping Orbit turns the home button off.** Android clears an app's entry from
`enabled_accessibility_services` when the app is force-stopped, and it does not come back when
the app is started again. Measured on the set: enabled and bound, then one `am force-stop`, and
the setting reads `null` with no service bound. Nothing warns about it, and the symptom is that
the home button quietly goes back to the TV maker's launcher. A reinstall over the top is fine
and keeps it; only force-stopping clears it. So after any force-stop, from the shell or from
the TV's own app settings, turn it on again:

```bash
adb shell settings get secure enabled_accessibility_services   # null means it was cleared
```

Note also that being the preferred home activity and the home button working are not the same
thing. On this set `dumpsys package preferred` names Orbit and the remote's home button still
opens the TV maker's launcher, because the key never reaches that resolution. Orbit treats
either one as good enough to stop showing its notice, so a set where the preference is recorded
but not honoured will not be warned that the service is off.

The service reads nothing from notifications or screen content. Its only capability is
`TYPE_WINDOW_STATE_CHANGED`, which is how it knows the stock launcher opened.

## Top bar

Left of the profile picture sit small buttons, in this order: the TV's own search,
Backdrops, All apps, its inputs panel, the Play Store, Settings and About. Press up from
the rows to reach them. A button is left out when the TV has nothing for it to open.

**All apps** opens the same grid as the add tile, but selecting an app there opens it.
Holding OK, or pressing Menu, raises a menu offering to add it to the favorites or take it
out, and to uninstall it. The rows show favorites only, so this is how an app that is not a
favorite gets opened.

**About** says what is running: the version, whether it is a `stable` or a `debug` build,
the commit it was built from, which release that commit is, and the day it was built. Any
key closes it. The commit is the useful part, since every build between two releases
carries the same version; compare it with the repository to see whether the TV is up to
date. A build from an untagged commit reads `between releases`, and one made with
uncommitted changes says so too.

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

A zero-byte screenshot while `Display Power: state=ON` is not a sleeping panel: it is
protected content on screen. Check what is in front first:

```bash
adb shell dumpsys activity | grep -m1 ResumedActivity
```

## Releasing

Versions are [semver](https://semver.org). The version lives in one place, `orbitVersion`
in `app/build.gradle.kts`, and `versionCode` is derived from it, so there is nothing to
keep in step by hand.

The bump has to be its own commit, and that commit is the one tagged. Tagging a commit that
still carries the old `orbitVersion` gives an APK whose version and tag disagree, which is
exactly what the About card exists to catch.

```bash
# 1. Bump orbitVersion in app/build.gradle.kts, then open a PR and merge it as usual.
# 2. Tag the merged commit and publish the release from it.
git fetch origin && git checkout main && git pull
git tag -a v0.3.1 -m "Orbit 0.3.1"
git push origin v0.3.1
gh release create v0.3.1 --title "Orbit 0.3.1" --generate-notes
```

`--generate-notes` writes the list from the pull requests merged since the previous tag.
Do not pass `--target`: with the tag already pushed it is redundant, and a short sha there
is rejected with `Release.target_commitish is invalid`.

Then build the APK the release names and put it on the TV, from a **clean** checkout of the
tagged commit: an uncommitted edit makes the About card read `with changes on top`, because
the build records whether the tree was dirty.

```bash
./gradlew --no-daemon :app:assembleRelease
"$ANDROID_HOME"/build-tools/35.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk | head -1   # versionName matches the tag
adb install -r app/build/outputs/apk/release/app-release.apk
```

Check it landed by opening About in the top bar: it should name the version, the commit and
`released as <tag>`.

No APK is attached and no workflow builds one, on purpose: the only consumer is one
television, which is flashed from a local build. The tag is what the About card's commit is
checked against. If packages are wanted later, the step to add is `gh release upload`,
not a new CI trigger.

## Dependency updates

Renovate, configured in `renovate.json`, which extends a shared preset. It covers
everything this project declares a version for: the Gradle dependencies and the plugins
in the `plugins {}` blocks, the Gradle wrapper, and the actions in the workflow.

Minor and patch updates automerge once the build is green, which is why the workflow
runs on pushes to `renovate/**` as well as to `main`: the shared preset merges the
branch itself, so the branch has to carry a status check.

Compose, Kotlin and the Android Gradle Plugin never automerge. Focus on this launcher
depends on Compose internals that a release can change without a word in its notes, and
the harness renders on a desktop JVM rather than the panel. Those come as one pull
request labelled `check-on-tv`, to be installed and driven by hand before merging:
pop-out on focus, the hero, the pickers, and Back and Home.

## Artwork, and why the second start is quicker

Covers and backdrops are published at full size. On the set this was written for, one cover is
1 to 2.4MB at 2000x3000, behind a provider that serves one at a time: nine of them finished
between 7.9 and 32.7 seconds after being asked for together. Decoding is not what that waits
for, measured at 0 to 1ms per picture. It is the bytes.

So Orbit scales each picture down to the size it actually draws and keeps that small copy in
`cacheDir`, where a later start reads it instead of the original. Measured on the same set, a
tile comes back in 22 to 93ms. Only the first start after an install is slow.

The store is capped at 24MB and anything unread for 30 days is dropped, which is also what
forgets artwork for media you deleted: it stops being published, so it stops being read, so it
leaves on its own. Files are named by a digest, so no server address or item id is written to
disk. Replaced artwork is picked up by itself, because the media server puts a hash of the
picture in its URI and a new picture is therefore a new URI.

Clearing the app's cache in the TV's settings empties it, and costs one slow start.

## What survives a reset

Favorites, their order, the chosen inputs, which apps supply backdrops and the profile
picture are included in Android's Auto Backup, in `res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml`. Naming anything means everything else is left out,
which is the point: `orbit.playback` holds the names of programmes the TV was seen
playing and `orbit.last` holds the app opened last. Neither is a setting, both say what
someone watched, and neither leaves the set.

Whether any of it is actually backed up depends on the TV having a backup transport:

```bash
adb shell bmgr enabled
adb shell bmgr list transports   # a * marks the one in use
```

On the set this was written for, the Backup Manager is enabled but no transport is
selected, so nothing is backed up today and `bmgr backupnow` answers `Transport error`.
The rules still matter for a set that has one, and for a transfer to another device.

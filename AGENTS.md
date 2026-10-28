# Notes for agents

For an agent asked to get Orbit running on someone's TV, or to change it. Everything here
cost a measurement to learn, and most of it is not guessable from the source.

Read [README.md](README.md) for what Orbit is and
[HARNESS.md](HARNESS.md) for how the test harness works. This file is the things that will
waste your time if you do not know them.

## The constraints, which are not negotiable

This repository is public, and the person it was written for treats every repository as
public.

- **No identifiers of any kind.** No IP addresses, hostnames, server URLs, account names,
  library item ids, MAC addresses, serial numbers or person names. Not in code, comments,
  tests, fixtures, commit messages or documentation.
- **Never log artwork or intent URIs.** A media server's poster URI carries the owner's
  server address and the id of something in their library; an app's intent URI carries its
  own addressing. Log counts, type enums, package names and shapes instead. This is why the
  device probes in this repo print numbers and nothing else.
- **No copyrighted assets.** The mark in `app/src/main/res/drawable/` is geometry written by
  hand for this reason: a traced bitmap or a rasterised system font would both be a question
  nobody wants to answer.
- Requirements, decisions and device notes live **outside** this repository, in the owner's
  own project directory. Do not move them in.

## Build and test

```bash
export JAVA_HOME=/path/to/jdk           # 17 or newer
export ANDROID_HOME=/path/to/android-sdk
./gradlew --no-daemon :app:testDebugUnitTest      # the whole suite
./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.orbit.launcher.MyTest'
./gradlew --no-daemon :app:lintDebug :app:assembleRelease
```

Lint must stay at its baseline count. A warning that is genuinely new is a bug in your
change, not a reason to grow `app/lint-baseline.xml`.

The suite runs Robolectric with native graphics and pins every core for a couple of minutes.
Batch your edits and run once. On a laptop this is noticeable to whoever is using it.

## What a green suite does not prove

This is the single most expensive lesson in this repository's history. Several bugs survived
fixes that passed their own tests.

- **Compose keeps the full string in semantics** however the text is visually truncated. An
  assertion through semantics cannot see clipping, an ellipsis, or a line that ran off the
  edge. If the claim is visual, measure bounds or look at a screenshot from the real panel.
- **Roborazzi records, it does not compare.** `roborazzi.test.record` is `true`, so the
  screenshots never fail a run. They are there to look at.
- **Rendering is not running.** A vector with bad path data draws nothing and the build
  passes. `BannerTest` rasterises the drawable and checks the colours that should be in it,
  which is the shape to copy for anything visual.
- **Do not let a test encode a guess.** Assert the relationship or the ceiling, never an
  exact number you assumed. Several tests here deliberately assert "A is larger than B" or
  "this fits inside that" for exactly this reason.

## Device truths that are not in any documentation

Verified on an Android 10 set. Re-verify on yours; do not assume.

- **`adb shell content query` cannot read `android.media.tv`.** It returns "No result found"
  for every table, including ones with over a thousand rows, because `com.android.shell` does
  not hold `READ_TV_LISTINGS` and cannot be granted it. The output is indistinguishable from
  an empty table. To see the provider, query it **from inside the app** and log counts.
- **`adb shell input keyevent 3` is not the home button.** It is injected into the input
  pipeline and does not take the path the physical remote takes. It will tell you home works
  when it does not. There is no substitute for someone pressing the button.
- **`am force-stop` silently turns off the home button.** It clears the app's entry from
  `settings secure enabled_accessibility_services` and does not restore it on relaunch. Use
  `am start` to bring the app forward and `adb install -r` to update it. Never force-stop.
- **The stock launcher may be unbeatable.** `cmd package set-home-activity` can report
  success, record nothing, and change nothing, because the maker's launcher declares
  `mPriority=2` on its own home filter. Check with `cmd package resolve-activity`, not with
  the command's exit status. Disabling it is the way through; see the README.
- **The TV provider caps `COLUMN_SHORT_DESCRIPTION` at 150 characters** and cuts mid word to
  get there. Prefer `COLUMN_LONG_DESCRIPTION`. A description that ends mid word is very
  likely the provider's doing and not yours.
- **An app will publish the same artwork twice.** Two episodes of one series carry the same
  series cover. A lazy row keyed on the artwork URI throws `Key ... was already used` and
  takes the launcher down. Never key a row on data another app controls.
- **`Modifier.height(IntrinsicSize.Min)` asks children for their height at a width**, and
  `fillMaxWidth(fraction)` does not survive that question: the child is asked at the full
  width and the row is then fixed at that height. Give the child a width in dp.
- **A `LruCache` handed one entry bigger than its maximum evicts everything and then that
  entry too**, leaving the cache empty. Check the size before putting.
- **`am kill` does not restart the launcher.** It only takes processes that are safe to kill,
  and the home app visible on screen is not one of them: the memory cache went on answering,
  so a run meant to measure a cold start measured nothing. `adb install -r` does restart the
  process, and unlike `force-stop` it leaves the accessibility setting alone. That is the only
  way found to get a real second start.
- **The artwork provider serialises and the payloads are the cost.** One cover is 1 to 2.4MB at
  2000x3000 and nine asked for together finished 7.9 to 32.7s after a common start, on nine
  separate threads, while decoding measured 0 to 1ms each. Parallelism and decode tuning buy
  nothing here. `ContentResolver.loadThumbnail` is not supported by it and fails in 15 to 59ms.
- **The media server's artwork URI carries an image tag.** It is
  `content://<authority>?src=<encoded url>&v=<constant>`, where the encoded url holds
  `tag=<32 hex>` derived from the picture itself. So a cache keyed on the whole URI invalidates
  on its own when artwork is replaced. The `v` parameter is the same for every item and is not
  a per-picture version; do not mistake it for one.
- **`adb shell input keyevent --longpress` is not a held key.** It sends a fresh DOWN with
  `repeatCount` 0 and no `FLAG_LONG_PRESS`, then a flagged DOWN, then UP. Anything that tells a
  deliberate press from a key being held, as the row edges do, sees the press half and never the
  hold. Measured on the set: one `--longpress` right from the last tile moved two tiles, because
  the first DOWN wrapped and the second moved on. Test holds in the harness and leave the feel
  of it to whoever has the remote.
- **A screencap of 0 bytes means the panel is off**, around 8KB means asleep. Neither is a
  failure. Wake it with `keyevent 224`, and put it back with `keyevent 223` when you finish.

## Working on someone's actual television

A TV is not a test device. Someone is probably watching it.

- **Screencap first.** If something is playing, the set is in use. Wait, and ask.
- **Ask before changing system state.** Disabling the stock launcher is reversible, but it is
  the owner's television and their decision.
- **Always leave a way back.** `com.android.tv.settings.system.FallbackHome` is part of
  Android, stays enabled and is direct-boot aware, so a TV whose launcher is disabled still
  has a home screen if your app will not start. Verify that before you disable anything.
- **Signing decides whether updates destroy settings.** Sign debug and release with the same
  key, or a signature mismatch forces an uninstall and the owner loses their favorites and
  the listings permission with them. See [Signing](README.md#signing).
- **Back up `shared_prefs` before anything destructive**, via `run-as` on a debuggable build.
  You cannot read them back into a release build.

## Code conventions here

- Comments say **why**, with the measurement that settled it. There are a lot of them, and
  they are the point: most carry a number taken off the real panel. Match that density.
- No em dashes anywhere. Rephrase, or use a comma, colon or parentheses.
- Test names are sentences describing the claim, in backticks.
- `ShotHooks` is the accepted seam for injecting fixtures into main source. Prefer it over
  widening visibility.
- Pure, testable functions over logic inside composables. `Takeover`, `pictureKeys`,
  `bestDescription`, `descriptionLines` and `scrimStrength` all exist because the decision
  was worth testing one input at a time.

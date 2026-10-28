# Orbit TV test harness

Drives the real Orbit screens under Robolectric at TV size (960x540dp, xhdpi, television)
with the key events a remote sends, and takes real screenshots (Roborazzi, native graphics).

Run one class: `./gradlew --no-daemon testDebugUnitTest --tests 'app.orbit.launcher.MyTest'`
Test stdout (println) is shown in the Gradle output. Screenshots go to `outDir`.

## What a green run does and does not prove

The assertions are on semantics, focus and bounds. `roborazzi.test.record` is on, so the
screenshots are recorded rather than compared: they are there to look at, not to fail on. A
green run means no logic or focus regression, never "verified". Robolectric cannot see the
things that have actually bitten on this TV: real focus timing, the leanback scroll pivot,
HDMI-CEC, and the system's own dialogs. Those still need the set.

## Pieces
- `ShotHooks` (main source, test-only doubles): `apps`, `inputs`, `store`, `storeIcon`,
  `allInputs`, `search`, `defaultHome` for the rows and the top bar; `watchNext`, `pictures`,
  `artwork`, `cycling` for the hero; `now` to pin the clock and `build` to pin the whole build
  stamp, so the About card and anything dated is deterministic in a screenshot.
  AppRepository/InputRepository return these when set, and `AppRepository.launchIntent`
  answers for the fake apps so launches show in `nextStarted()`. Robolectric has no real
  launcher apps or TvInputManager, so without them rows are empty.
- `TvTest` (test source): base class with the fixture, `favorites()`, `stored()`, `pump()`,
  `launch()`, `press()`, `key()`, `holdOk()`, `shot()`, `focused()`, `nextStarted()`.
- `HomeShots`: worked examples (menu, move, hold OK, absent inputs...).

## Pitfalls already learned (each one produced a fake bug before it was understood)
1. **Touch mode.** A TV is never in touch mode. Robolectric starts in it, and in touch mode
   Compose's `clickable` cannot take focus, so OK does nothing. `launch()` turns it off.
   Always launch through `launch()`.
2. **Key timestamps.** Real key events carry a downTime. `tileKeys` uses it to guard the
   long press; an event with downTime 0 is swallowed. Use `press()`/`key()`, never
   `KeyEvent(action, code)`.
3. **State between frames.** A state write from a key handler only applies on the next frame
   flush. `pump()` flushes snapshots, advances the Compose clock and idles the looper with
   time. Without it the screen lags one key behind.
4. **Never autoAdvance.** Scrolling labels (basicMarquee) animate forever; an auto-advancing
   clock never settles. `fixture()` sets autoAdvance = false; use `pump()`.
5. **Qualifier order** matters: `w960dp-h540dp-land-television-xhdpi`.
6. **OK and focus.** OK moves Compose focus from a tile's `focusable()` into the `clickable`
   inside it while the key is down; the click fires on the release. That is real device
   behaviour, not a harness quirk.
7. **Started activities** are recorded, not started: read them with `nextStarted()`.
   Launch other screens (pickers) with their own `launch(...)`.
8. **Leaving and coming back**: `scenario.moveToState(Lifecycle.State.CREATED)` then
   `moveToState(RESUMED)`, or `scenario.recreate()`; HomeActivity bumps Returns.count onResume.
9. Static singletons (FavoriteStore.revision, Returns.count, AppRepository cache, ShotHooks)
   live across tests in one class. SharedPreferences are reset per test.
10. Gradle needs ~/.gradle/init.d/central-mirror.gradle (Maven Central rate-limits this
    machine); Robolectric's own downloads use the mirror set in app/build.gradle.kts.
    On a machine where Maven Central works, the mirror lines can go.
11. **Background reads race the fake clock.** The app reads apps, inputs and pictures on
    Dispatchers.IO, which runs on real threads, while `pump()` advances fake time. A test that
    waits for a reload (say, after leaving and coming back) should loop until the state shows
    rather than trust one fixed pump. Running several Gradle builds at once makes it worse.
12. **Focused bounds and the pop-out.** The focused node's `boundsInRoot` keeps the tile's own
    left edge and adds the grown (1.2x) width. Its own right edge is `left + 111dp`.
13. **Focus that the system places does not count.** When a window opens, or the focused item
    disappears, Android hands focus to the first thing on screen (the top bar). Orbit only
    records where focus is for moves made while a remote key is being handled.

## The suites
Around forty classes; these are the ones worth knowing by name.
- `AppsRowTvTest`, `InputsTopBarTest`, `VerifyBackHomeTest`, `HomeReachTest`: the home screen
  (rows, top bar, notice, Back and Home). `bug_*`/`bugN_*` tests are the bug-hunt repros; they
  fail on code before PR #11 and pass with it.
- `PickerExplore`, `PickerMenuTvTest`: the pickers, and the hold-OK menu on All apps.
- `UninstallEntryTvTest`: which removal entry a tile offers, which depends on whether the app
  is removable, a factory app that updated, or a plain factory app.
- `TileKeysTest`: the key rules on their own, including the edge case where a held key stops
  at the end of a row and a fresh press comes round. A real remote is the only way to feel
  that one; `adb shell input keyevent --longpress` cannot reproduce a held key.
- `AboutTvTest`: the About card, against a pinned `ShotHooks.build`.
- `AvatarFlowTest`, `VerifyAvatar`: the avatar picker (PR #9) and, in its `b*` tests, the home
  button service (PR #13).
- `BannerTest`: rasterises a drawable and checks its colours, the shape to copy for anything
  visual.
- `HomeShots`, `TvTestSmoke`: screenshots and a smoke test.
Screenshots go to `app/build/tv-shots/`.

## Telling real bugs from harness artifacts
Before reporting, ask: would a real remote on a real TV do the same? Re-check pitfalls 1-4.
If a behaviour depends on something Robolectric fakes (PackageManager, MediaStore, TvInputManager,
permission dialogs, other apps' activities), say so and lower your confidence.

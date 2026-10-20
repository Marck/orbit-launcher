# Orbit launcher

A replacement home screen for an Android TV set: one screen, no advertising rows,
a hero for what you were last watching, a scrolling row of pinnable apps, and a row
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

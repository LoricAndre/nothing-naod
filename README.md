# Shake Time

An Android app for the **Nothing Phone (4a) Pro** that flashes the current time
on the Glyph Matrix when you **shake the phone while it is lying face-down** on a
surface — like the built-in clock Glyph Toy, but only on demand and only for a
few seconds. It also exposes a shortcut so you can trigger the reveal manually or
from automation apps such as Tasker.

## How it works

- A lightweight foreground service watches the accelerometer.
- It only reacts when the phone is **face-down and roughly flat** (screen toward
  the table). A couple of quick jolts within ~1.2 s count as a shake; a single
  bump does not.
- On a shake it renders `HH` over `MM` (two rows, compact 3×5 pixel font, sized
  to the 13×13 matrix) and pushes it to the Glyph Matrix via the SDK's
  `setAppMatrixFrame`, then clears it after the configured duration.
- A cooldown prevents re-triggering while the time is still showing.

The reveal path is shared by the background monitor, the launcher shortcut, and
the automation entry points, so they all behave identically.

## Triggers

**1. Shake (background).** Toggle *"Shake to show time"* in the app. This starts
the monitor and keeps a low-priority notification (required for a foreground
service). It restarts after reboot if left enabled.

**2. Launcher shortcut.** Long-press the app icon → *Show time*, or tap
*"Add home-screen shortcut"* in the app to pin it.

**3. Automation apps / adb.** Two equivalent entry points:

- Broadcast (recommended for Tasker → *Send Intent*, target *Broadcast Receiver*):

  ```
  Action: dev.orsetto.shaketime.action.SHOW_TIME
  ```
  ```sh
  adb shell am broadcast \
    -a dev.orsetto.shaketime.action.SHOW_TIME \
    -n dev.orsetto.shaketime/.TriggerReceiver
  ```

- Launch the invisible activity:

  ```sh
  adb shell am start -n dev.orsetto.shaketime/.ShowTimeActivity
  ```

Both accept an optional long extra `duration_ms` to override the display time,
e.g. `--el duration_ms 3000`.

## Settings

- **Display duration** — 1.5 s to 10 s.
- **Shake sensitivity** — higher means a gentler shake triggers it.
- **Brightness** — 1–255 LED brightness.

Hours/minutes follow your system 12/24-hour setting.

## Requirements

- Nothing Phone (4a) Pro (`Glyph.DEVICE_25111p`). The renderer also adapts to the
  Phone (3) 25×25 matrix if you run it there.
- Nothing OS with the Glyph Matrix service, **system build 20250801 or later**
  for app-based matrix control (`setAppMatrixFrame`). Older builds fall back to
  `setMatrixFrame`.
- Android 14+ (`minSdk 34`, `targetSdk 35`).

## Building

Requires the Android SDK (command-line tools or Android Studio) and a JDK 17+.

```sh
./gradlew assembleDebug
```

### The Glyph Matrix SDK

The app depends on Nothing's **Glyph Matrix SDK** (`glyph-matrix-sdk-2.0.aar`).
That library is closed-source and its licence forbids redistribution, so it is
**not** committed here. The Gradle build downloads it automatically from
[Nothing's official developer kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
into `app/libs/` on first build.

If your build machine is offline, fetch it ahead of time:

```sh
./scripts/fetch-glyph-sdk.sh
# or download glyph-matrix-sdk-2.0.aar manually and drop it in app/libs/
```

## Continuous integration & releases

GitHub Actions (`.github/workflows/build.yml`) builds a debug APK on every push
and pull request and uploads it as a workflow artifact (`shake-time-debug`).

To cut a release, push a tag starting with `v`:

```sh
git tag v1.0.0 && git push origin v1.0.0
```

That builds `assembleRelease` and attaches the APK to a GitHub Release.

- With **no secrets configured**, the release APK is signed with the debug key
  so it is still installable.
- For a properly signed release, add these repository secrets and the workflow
  will use them automatically: `KEYSTORE_BASE64` (base64 of your keystore),
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Dependabot (`.github/dependabot.yml`) opens weekly PRs for Gradle dependencies
and GitHub Actions.

## Enabling the Glyph on the device

The app uses **app-based matrix control**, so it does not need to be added to the
Glyph Toys carousel. Just install it, grant the notification permission when
asked, and trigger it. If nothing appears:

- Make sure Glyph Interface is enabled in *Settings → Glyph Interface*.
- Confirm your Nothing OS build is recent enough for `setAppMatrixFrame`
  (20250801+).

## Notes & limitations

- Continuous accelerometer monitoring uses battery; turn the toggle off when you
  don't need it.
- Glyph Toys have display priority over app control — interacting with the Glyph
  Button can override the app's output.
- The face-down + shake heuristic is tuned conservatively; adjust *sensitivity*
  if it triggers too easily or not enough.

## Project layout

```
app/src/main/java/dev/orsetto/shaketime/
  MainActivity.kt          Settings UI
  ShowTimeActivity.kt      Invisible trampoline (shortcut / automation)
  TriggerReceiver.kt       Broadcast entry point for automation
  BootReceiver.kt          Restarts monitoring after reboot
  ShakeMonitorService.kt   Foreground service: sensors + one-shot reveals
  ShakeDetector.kt         Face-down + shake detection
  GlyphClock.kt            Drives the Glyph Matrix SDK
  TimeMatrixRenderer.kt    Renders HH/MM to a matrix-sized bitmap
  Prefs.kt                 Settings storage
```

## License

App code: MIT (see `LICENSE`). The Glyph Matrix SDK is proprietary to Nothing
Technology Limited and governed by its own EULA.

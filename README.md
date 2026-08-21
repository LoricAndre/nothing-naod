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
*"Add home-screen shortcut"* in the app to pin it. There is also a standalone
**"Show Time"** launcher icon (an activity-alias) you can drop on your home
screen for one-tap access.

**3. Automation apps (easiest).** The **"Show Time"** entry appears in
automation apps' app pickers, so no intent editing is needed:

- **Tasker** → *Add Action* → *App* → *Launch App* → pick **Show Time**.
- Most other automation apps (MacroDroid, Automate, launchers) list it the
  same way as a launchable app/shortcut.

**4. Automation apps / adb (explicit intents).** If you prefer sending an
intent directly:

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

Brightness is not set by the app — the time is drawn at full and the **system
Glyph Interface brightness** (Settings → Glyph Interface, including adaptive)
governs the actual LED brightness.
- **Flash icon on new notification** — when a notification arrives while the
  phone is lying face-down, briefly shows that app's icon on the Glyph Matrix
  (~3 s). The icon is rendered as a silhouette sized to the matrix. Repeats of
  the same notification and rapid bursts are suppressed. Requires Notification
  access; works whether or not the shake monitor is running.
- **Show unread count on Glyph** — overlays the notification count on the four
  right-hand pixels as a binary counter (top = 8, then 4, 2, bottom = 1; capped
  at 15). Set bits are full brightness; unset bits stay dimly lit so all four
  positions are visible. Requires granting Notification access; ongoing
  notifications (including this app's own), group summaries, and silent
  (low-importance) notifications are excluded.

Hours/minutes follow your system 12/24-hour setting.

## Requirements

- Nothing Phone (4a) Pro (`Glyph.DEVICE_25111p`). The renderer also adapts to the
  Phone (3) 25×25 matrix if you run it there.
- Nothing OS with the Glyph Matrix service, **system build 20250801 or later**
  for app-based matrix control (`setAppMatrixFrame`). Older builds fall back to
  `setMatrixFrame`.
- Android 14+ (`minSdk 34`, `targetSdk 36`).

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

GitHub Actions (`.github/workflows/build.yml`) builds a release APK on every
push and pull request and uploads it as a workflow artifact
(`shake-time-release`). Pushing a tag starting with `v` additionally attaches
the APK to a GitHub Release.

### Signing (install updates without reinstalling)

Android only lets an APK update an installed one when both share the **same
signing key**. Configure a stable key once and every build installs in place:

1. Create a keystore (once):
   ```sh
   keytool -genkey -v -keystore shake-time.jks -alias shaketime \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode it:
   ```sh
   base64 -w0 shake-time.jks   # macOS: base64 -i shake-time.jks
   ```
3. In the repo, add **Settings → Secrets and variables → Actions** secrets:
   - `KEYSTORE_BASE64` – the base64 string from step 2
   - `KEYSTORE_PASSWORD` – the keystore password
   - `KEY_ALIAS` – `shaketime` (or your alias)
   - `KEY_PASSWORD` – *optional.* `keytool` creates a **PKCS12** keystore, where
     the key uses the **same password as the keystore**, so you can omit this
     secret. If you do set it, it **must equal** `KEYSTORE_PASSWORD` — a
     different value fails signing with "Get Key failed: Given final block not
     properly padded."

CI signs the release APK with that key automatically. With **no secrets set**,
the release APK falls back to an ephemeral debug key (installable, but each build
has a different signature, so it won't update over a previous install).

Keep the `shake-time.jks` file safe and out of git — losing it means you can no
longer ship updates over an existing install.

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

- **Detection mode** (in the app) trades battery for reliability when the phone
  is face-down with the screen off:
  - *Reliable* — holds a wake lock for the whole session so events always arrive
    while asleep. Works on any phone; uses the most battery.
  - *Balanced* — uses a wake-up accelerometer with no session wake lock; much
    lighter, but only detects while asleep if the phone has a wake-up sensor.
  - *Screen on only* — no wake lock; detects only while the screen is on; least
    battery.
  Turn the monitor off entirely when you don't need it.
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

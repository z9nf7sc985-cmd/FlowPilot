# FlowPilot

Visual Android automation builder inspired by WinAutomation, Tasker, and MacroDroid. Build ordered flows of taps, swipes, image finds, conditions, loops, and more — for **personal productivity** automation.

**Target device:** Samsung Galaxy S9+ (Android 10 / API 29). `minSdk 26`, `targetSdk 29`.

## Disclaimer

FlowPilot is for **personal productivity automation**. You must follow the terms of service of any apps you automate. Do **not** use it to cheat in multiplayer games, hide the app, or evade detection by other apps. The authors are not responsible for misuse.

## What's new in 0.2.1

Hardening for Galaxy S9+ / Android 10: editor refreshes after Add/Edit step, Run waits for save, schedule enable/disable arms AlarmManager correctly, Import/Export toasts, MediaProjection kept alive by a capture foreground service, one-tap battery-unrestricted prompt, image-matcher memory leak fixed. Same upload keystore as 0.2.0 (versionCode 4) — installs as an update.

## What's new in 0.2.0

Major UX pass: searchable **app picker** (no typing package names), nested visual step builder with human-readable sentences, pick-from-screen tools (element / point / image crop / swipe), setup permission checklist, 12 ready-made templates, home-screen pin shortcuts, and a **partial** Record mode (taps, swipes, app opens — not typed text). Same debug keystore / `com.flowpilot.automation.debug` so it updates over 0.1.1.

## Install (sideload on Galaxy S9+)

1. Copy `FlowPilot-debug.apk` to the phone (USB, Drive, etc.).
2. Settings → Apps → Special access → **Install unknown apps** → allow your file manager / Files / Chrome.
3. Open the APK and install.
4. Open **FlowPilot** → complete onboarding.

### Required permissions

| Permission | Why |
|---|---|
| **Accessibility** (Settings → Accessibility → Installed apps → FlowPilot) | Tap, swipe, type, click-by-text, read UI |
| **Display over other apps** | Floating start/stop + coordinate picker |
| **Screen capture** (prompt from Setup, API 29) | Find-image / screenshots on Android 10 |
| **Battery → Unrestricted** (Samsung Device care) | Keeps Accessibility + schedules alive |
| Notifications | Runner persistent notification + STOP |

### Samsung quirks

- After reboot, **re-check Accessibility** — One UI may disable it.
- Set battery usage to **Unrestricted**; turn off “Put unused apps to sleep” for FlowPilot.
- Exact alarms may need allowing under Alarms & reminders on newer One UI builds.

## Features

### Implemented (v1)

- Flow list editor: add / edit / reorder / delete / duplicate steps; save as local JSON; import/export JSON (clipboard)
- Nested then/else children for If / Try (helper buttons + JSON)
- Triggers: Manual, Schedule (daily or interval), App opened, Notification, Boot, Battery, Wi-Fi
- Accessibility actions: tap/long-press/swipe, type, Back/Home/Recents, click by text/id/desc, read text/screen, wait for element, open app/URL, clipboard/paste, screenshot
- Image recognition: find template (pure-Kotlin NCC, downscaled), confidence + region + timeout, tap found image; in-app screenshot crop to save template
- Logic: variables (`{{name}}`), set/math/string ops, If/ElseIf/Else, While, For, Break/Continue, Wait, random delay, Stop, Run automation, Try/On error, Log
- Other: notification, toast, TTS, vibrate, play sound, HTTP GET/POST, read/write app files, SMS if permitted, launch intent
- Foreground runner service with persistent notification + STOP; MediaProjection capture foreground service; run log; step highlight; QS kill-switch tile; floating overlay; coordinate picker
- Scheduling via **AlarmManager** (`setExactAndAllowWhileIdle`) for daily/interval triggers (WorkManager is on the classpath but not used for schedules)
- 3 starter example automations
- Unit tests for variables / conditions / serialization

### Not yet / limited

- Full nested visual editor for deep trees (use helper buttons or JSON)
- OpenCV (uses pure-Kotlin matcher instead)
- MediaProjection foreground-service UX polish on API 29 (one-time grant from Setup)
- Home-screen shortcuts per automation (intent action exists: `RUN_AUTOMATION`)
- Rich Else-If branch UI (model supports it; editor is minimal)
- Device-tested on physical S9+ — **not tested on a real device in CI** (no emulator/KVM here)

## Build

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug test
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push to `main` runs `.github/workflows/build.yml`, which builds the debug APK and uploads it as a workflow artifact (and attaches to a release when tagged `v*`).

## Block reference (short)

See in-app **Add step** catalog. Placeholders like `{{foundX}}` expand from variables. Conditions: `eq/neq/lt/lte/gt/gte/contains/...` plus special `kind` values `element_exists`, `image_found`, `battery`, `time_of_day`, `app_foreground`.

## License

MIT — use responsibly.


## Troubleshooting: "There was a problem parsing the package"

On Galaxy S9+ / Android 10 this usually means a corrupt download **or** an APK signed without a usable JAR (v1) signature / with API-34-only manifest bits. Use the [v0.1.1 release](https://github.com/z9nf7sc985-cmd/FlowPilot/releases/tag/v0.1.1) APK, verify the SHA-256, and install from the phone's Files app (not a renamed/incomplete transfer).

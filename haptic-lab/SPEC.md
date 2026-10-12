# Haptic Lab

## Purpose

Haptic Lab is an offline native Android app for studying tactile reading. A finger moves along a horizontal track, and the phone vibrates according to the finger’s zone rather than a timer started in advance. The first device of interest is an Honor 400 on MagicOS. The audience is the researcher running the session, not a general store user.

## Requirements

1. Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL. No Flutter, React Native, or WebView. No root and no hidden APIs.
2. Toolchain matches this monorepo: Android Gradle Plugin 8.9.2, Kotlin 2.1.21, Gradle 8.11.1, JVM bytecode 17, `compileSdk` 36, `targetSdk` 36. `minSdk` is 26, an explicit exception to the monorepo default of 34, because the research brief requires API 26 with version checks. Package and `applicationId` are `com.tepmex.hapticlab`.
3. Release builds are minified and resource-shrunk, signed with the shared sideload keystore, and published from `site/index.html`.
4. The app works offline. No analytics, accounts, or network calls.
5. Six Russian screens:
   - Diagnostics: manufacturer, model, Android release, `SDK_INT`, `SDK_INT_FULL` when present, vibrator count via `VibratorManager` from API 31 (plain `Vibrator` before that), amplitude control, composition primitives, public predefined effects, API 34 resonant frequency and Q factor. NaN or a missing value is shown as «Не предоставляется устройством». When both Q and f0 are finite, show `t10 = Q·ln(10)/(π·f0)` and label it as free decay, not a measured stop time. API 36 envelope support, frequency profile, and envelope limits. API 37.2 `VibrationEffect.Builder` and presets are not compiled, because they are absent from SDK 36; the screen says so. Millimeter calibration and a verbose-log switch live here.
   - Generator: one-shot, one waveform for a whole pulse train (not a delayed `vibrate()` per pulse), predefined effects, composition primitives, and API 36 basic and waveform envelopes when the device reports support. Presets 20/20, 20/10, 10/10, 50/20, and 100/50 ms. Stop calls `cancel()`. The diagram is the requested command, not a measured vibration.
   - Spatial patterns: 3–20 zones, width in millimeters, tap to edit, examples, saved patterns, both directions, stop when the finger lifts, a zone indicator, speed in mm/s from DPI plus a manual scale, boundary crossings and ON/OFF transitions. A state machine issues vibration only on a state change or a finite hold refresh, never once per motion event. Debounce is configurable and 0 disables it. Hold inside an ON zone is a finite command of about 2 seconds, refreshed near the end and marked `refresh=true`. Command times are logged, not physical response.
   - Speed test: alternating 1–10 mm segments, instantaneous, mean, and peak speed, crossings, traversal time, requested switch rate, a 1–5 rating, and save.
   - Psychophysics: one versus two pulses, gaps 10, 15, 20, 30, 40, 50, 75, 100, and 150 ms, randomized, with single-pulse controls. Parameters stay hidden until the answer. Report accuracy, the false-“two” rate, and a 95% Wilson interval. Optional 2-down/1-up staircase. Export and compare sessions.
   - Tactile alphabet: a few demo glyphs, not the full Russian alphabet. Configurable zones per glyph. Continuous multi-symbol reading in both directions, with an inter-symbol gap reserved for a future physical guide. The app does not draw that guide.
6. Logging uses a monotonic clock. `MotionEvent` timestamps are mapped onto `elapsedRealtime`. Logs store coordinates, speed, zone, the requested vibration, the API call time, pattern parameters, device parameters, and answers. Motion samples are throttled. A verbose switch can disable them. Do not log every point.
7. Three levels stay separate. A is commands and input. B is the physical actuator response, which the Vibrator API cannot supply; the app does not pretend an accelerometer measures the LRA. C is the human answer.
8. `VIBRATE` is declared. Vibration stops when the app backgrounds, when the user cancels, and when the finger lifts. Commands are bounded. The main thread is not blocked by file export.

## Interfaces

- Android vibrator APIs only, gated by `SDK_INT` and capability queries: `Vibrator`, `VibratorManager` (31+), `VibrationAttributes` (33+), `getQFactor` / `getResonantFrequency` (34+), `BasicEnvelopeBuilder`, `WaveformEnvelopeBuilder`, `areEnvelopeEffectsSupported`, frequency profile, and envelope effect info (36+).
- No `VibrationEffect.Builder`, `VibrationEffect.Preset`, `isPresetSupported`, or `getPresetDuration`.
- Sessions export as CSV and JSON through the system share sheet and `FileProvider` (`${applicationId}.fileprovider`).
- Preferences and saved patterns stay in app-private files. No remote API.
- Landing page: `site/index.html` links to `haptic-lab.apk`. CI registers the folder in `ANDROID_APPS`.

## Data model

- `DeviceSnapshot`: manufacturer, model, Android release, SDK, vibrator count, amplitude control, resonant frequency, Q, free-decay t10, envelope support.
- `NamedPattern`: title plus a boolean zone mask.
- `ExperimentSession` and `LogEvent`: monotonic timestamps, coordinates, speed, zone, requested command, API call time, and answers. Kinds include zone, vibration command, throttled motion, and human responses.
- `LabPreferences`: calibration scale, verbose logging, saved patterns, alphabet.
- `TactileAlphabet`: demo glyphs (А Б В Г Д Е and space by default) resized to a chosen zone count.
- Psychophysics trials: gap, one-or-two pulses, hidden until answered, then correctness. Summaries store proportion, Wilson bounds, and the false-“two” rate.

## UI / UX

Russian Material 3, dark amber theme. Home lists the six sections. Spatial, speed, and alphabet screens share a finger track that pans and converts pixels to millimeters. Blind mode on the spatial screen hides the zone, ON/OFF state, and crossing counts; speed stays visible. The experiment screen waits after playback, then accepts «один» or «два», and can compare earlier sessions. Diagnostics accepts a ruler measurement to set the millimeter scale.

## Out of scope

- Measuring actuator motion with the accelerometer or any other onboard sensor.
- API 37.2 builder and preset playback until this monorepo’s `compileSdk` includes them.
- A full Russian tactile alphabet, a physical guide rail, accounts, a server, or Play distribution.
- Treating `t10`, primitive-duration estimates, or command timestamps as measured motor stop times.
- Infinite vibration loops. Holds stay finite so an OEM duration cap cannot desync the state machine.

## Acceptance criteria

- `cd haptic-lab && ./gradlew :core:test` passes. Tests cover waveform construction and the 15 s cap, pattern examples, zone hit-testing, boundary penetration, the vibration state machine (no extra restart, debounce and disabling it, stop on lift and off-track, hold refresh, time budget), Wilson intervals, the randomized schedule with controls, the false-“two” rate, the staircase, `t10`, calibration, `MotionEvent` time mapping, speed, alphabet concatenation, log throttling, and CSV.
- `./gradlew :app:assembleRelease` produces an APK signed with `android/expected-sideload-cert-sha256.txt`.
- `scripts/ci-deploy-plan.test.sh` passes with `haptic-lab` in `ANDROID_APPS`.
- On a device, unsupported vibrator calls are refused with a reason, vibration stops on lift and background, and CSV/JSON export opens the share sheet. Those device checks are not run in CI.

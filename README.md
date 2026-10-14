# TactileReadingExperiments

Monorepo for tactile-reading experiments. Each app lives in its own top-level folder and is built and deployed independently to a matching path on **project** GitHub Pages.

The layout, CI pipeline, and Android sideload signing match [CloudAgenticCoding](https://github.com/TepMex/CloudAgenticCoding).

**Live site base:** [https://tepmex.github.io/TactileReadingExperiments/](https://tepmex.github.io/TactileReadingExperiments/)

## Apps

| Folder | Live site | Description |
| ------ | --------- | ----------- |
| [haptic-lab](haptic-lab/) | [haptic-lab](https://tepmex.github.io/TactileReadingExperiments/haptic-lab/) | Native Android lab for spatial tactile reading: vibrator diagnostics, a signal generator, zone patterns, a speed test, psychophysics, and a small tactile alphabet. |
| [vibe-alphabet](vibe-alphabet/) | [vibe-alphabet](https://tepmex.github.io/TactileReadingExperiments/vibe-alphabet/) | Native Android trainer for six vibration signals distinguished by on/off timing, with learn, test, and duration settings. |

## Pipeline

`.github/workflows/deploy.yml` runs on every push to `main`:

- **Web** apps: `bun install --frozen-lockfile` and `bun run build`. Output is `dist/index.html`, published at `/<repository>/<folder>/`.
- **Android** apps: Java 21, Android SDK, `assembleRelease`, then a check that the APK is signed with the shared sideload certificate. Published at `/<repository>/<folder>/<folder>.apk` with `site/index.html`.
- Unchanged apps are copied from the existing `gh-pages` branch. A manual **workflow_dispatch → full_deploy** rebuilds everything.

Develop from inside the app directory. Shared scripts live in `scripts/`. Shared Android signing and landing CSS live in `android/`.

## Adding a web app

1. Create a top-level folder with `SPEC.md` and a Bun app whose `build` script writes `dist/index.html`.
2. Honor `GH_PAGES_PUBLIC_PATH` (CI sets `/TactileReadingExperiments/<folder>/`).
3. Append the folder to `WEB_APPS` in `scripts/ci-apps.sh`.

## Adding an Android app

Rules for agents are in `.cursor/rules/android-apps.mdc`. Short version:

1. Top-level Kotlin / Compose project (`minSdk` 34, `compileSdk` / `targetSdk` 36) with `SPEC.md`.
2. Copy `android/sideload.keystore` and `android/sideload-signing.properties` into the app and apply `android/sideload-signing.gradle.kts` from `app/build.gradle.kts`.
3. Add `site/index.html` with a download link to `<folder>.apk` and a `<!-- deploy-footer -->` pair.
4. Append the folder to `ANDROID_APPS` in `scripts/ci-apps.sh`.

APKs signed with that keystore upgrade in place. A different key means uninstalling once before the next install.

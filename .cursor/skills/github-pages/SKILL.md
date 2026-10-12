---
name: github-pages
description: GitHub Pages deploy for this monorepo — base path, selective workflow, and local builds.
---

# GitHub Pages

The site is published from the `gh-pages` branch. Each app lives under **`/<repository>/<folder>/`** on the project GitHub Pages URL (not at the domain root).

Live base: `https://tepmex.github.io/TactileReadingExperiments/`

## How it works

1. **Catalog**: `scripts/ci-apps.sh` lists `WEB_APPS`, `ANDROID_APPS`, and `STATIC_ROOT_FILES`. Adding an app means appending it there.
2. **Web build**: from the app directory, `bun run build` must write `dist/index.html`. CI sets `GH_PAGES_PUBLIC_PATH` to `/<repository>/<folder>/` (defaults to `./` when unset for local builds).
3. **Android build**: CI runs `scripts/ci-build-android.sh`, which runs `sync-web-assets.sh` when present, then `assembleRelease`, then checks the shared sideload certificate. The APK is published as `<folder>/<folder>.apk` next to `site/index.html`.
4. **CI** (`.github/workflows/deploy.yml`): on each `main` push, only apps whose folder (or shared Android/signing paths) changed are rebuilt. Unchanged apps are copied from the existing `gh-pages` branch. Use **workflow_dispatch → full_deploy** to force a full rebuild. Editing the workflow or CI helper scripts does not rebuild every app.
5. **Deploy layout**: web `dist/` is copied to `deploy/<folder>/`, Android landings and APKs to `deploy/<folder>/`, plus `deploy/.nojekyll`. `peaceiris/actions-gh-pages` publishes `deploy`.

## Commands

```bash
cd <app>
bun install

# Local dev (assets at ./)
bun dev

# Production-like build matching GitHub Pages
GH_PAGES_PUBLIC_PATH=/TactileReadingExperiments/<app>/ bun run build
```

Android, from the app directory:

```bash
./gradlew assembleRelease
```

## Changing the subfolder

The public path is derived from `GITHUB_REPOSITORY` and the folder name in `scripts/ci-build-web.sh`. Keep `GH_PAGES_PUBLIC_PATH` starting and ending with `/`.

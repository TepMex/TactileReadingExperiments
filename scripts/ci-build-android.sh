#!/usr/bin/env bash
# Build one release APK and verify the shared sideload certificate.
# Run from the repository root after Java and the Android SDK are installed.
set -euo pipefail

app="${1:?Usage: ci-build-android.sh <app>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Wrappers that bundle a web app ship scripts/sync-web-assets.sh.
# Register the wrapper in android_web_runtime so CI installs Bun or Node first.
if [[ -f "$app/scripts/sync-web-assets.sh" ]]; then
  chmod +x "$app/scripts/sync-web-assets.sh"
  "$app/scripts/sync-web-assets.sh"
fi

(
  cd "$app"
  chmod +x gradlew
  ./gradlew --stacktrace assembleRelease
)

apk="$app/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  echo "Build failed: $apk is missing" >&2
  exit 1
fi

chmod +x android/verify-apk-sideload-cert.sh
android/verify-apk-sideload-cert.sh "$apk"

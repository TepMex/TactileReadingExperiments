# Shared app catalog for GitHub Pages deploy.
# Adding an app: append it here. Do not duplicate the list in deploy.yml.
#
# Web app:    folder with `bun run build` → dist/index.html
# Android app: folder with gradlew, app/build outputs, and site/index.html
# A web app bundled into an Android wrapper also needs android_for_web
# and android_web_runtime below, plus <wrapper>/scripts/sync-web-assets.sh.

WEB_APPS=(
)

ANDROID_APPS=(
  haptic-lab
  vibe-alphabet
)

# Repo-root files copied onto the site on every publish.
STATIC_ROOT_FILES=(
)

# Script changes that must refresh Android landing HTML (not CI helpers).
LANDING_SCRIPT_FILES=(
  scripts/inject-static-site-footer.ts
  scripts/deploy-metadata.ts
)

SIGNING_FILES=(
  android/sideload-signing.gradle.kts
  android/sideload.keystore
  android/sideload-signing.properties
  android/verify-apk-sideload-cert.sh
  android/expected-sideload-cert-sha256.txt
)

# Runtime for a web app build. Echo "node" for an npm app; everything else is Bun.
web_runtime() {
  case "$1" in
    *) echo bun ;;
  esac
}

# Runtime required to sync a bundled web app before the Android Gradle build.
# Echo "bun", "node", or "none".
android_web_runtime() {
  case "$1" in
    *) echo none ;;
  esac
}

# Android wrapper that must rebuild when its web app changes. Echo "" if none.
android_for_web() {
  case "$1" in
    *) echo "" ;;
  esac
}

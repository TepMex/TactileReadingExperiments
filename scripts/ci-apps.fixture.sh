# Fixture catalog for pipeline tests. Production deploy reads scripts/ci-apps.sh.
# Names are chosen so a prefix (pair vs pair-android) must not over-select.

WEB_APPS=(
  alpha
  beta
  pair
)

ANDROID_APPS=(
  notes-app
  pair-android
  kept-android
)

STATIC_ROOT_FILES=(
  fixture-static.html
)

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

web_runtime() {
  case "$1" in
    *) echo bun ;;
  esac
}

android_web_runtime() {
  case "$1" in
    pair-android) echo bun ;;
    *) echo none ;;
  esac
}

android_for_web() {
  case "$1" in
    pair) echo pair-android ;;
    *) echo "" ;;
  esac
}

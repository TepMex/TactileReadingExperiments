#!/usr/bin/env bash
# Assemble deploy/ from the previous gh-pages tree plus fresh build artifacts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=ci-apps.sh
source "${CI_APPS_SH:-$ROOT/scripts/ci-apps.sh}"

HAS_PREVIOUS="${HAS_PREVIOUS:-false}"
REFRESH_ANDROID_LANDINGS="${REFRESH_ANDROID_LANDINGS:-false}"
WEB_MATRIX="${WEB_MATRIX:-[]}"
ANDROID_MATRIX="${ANDROID_MATRIX:-[]}"
PREVIOUS_SITE="${PREVIOUS_SITE:-previous-site}"
ARTIFACTS_DIR="${ARTIFACTS_DIR:-artifacts}"
DEPLOY_DIR="${DEPLOY_DIR:-deploy}"

is_true() { [[ "${1:-}" == "true" ]]; }

mkdir -p "$DEPLOY_DIR"
touch "$DEPLOY_DIR/.nojekyll"

if is_true "$HAS_PREVIOUS" && [[ -d "$PREVIOUS_SITE" ]]; then
  shopt -s dotglob
  for item in "$PREVIOUS_SITE"/*; do
    [[ -e "$item" ]] || continue
    base="$(basename "$item")"
    [[ "$base" == ".git" ]] && continue
    cp -a "$item" "$DEPLOY_DIR/"
  done
  shopt -u dotglob
fi

for static_file in "${STATIC_ROOT_FILES[@]}"; do
  if [[ ! -f "$static_file" ]]; then
    echo "Missing static root file $static_file" >&2
    exit 1
  fi
  cp "$static_file" "$DEPLOY_DIR/$static_file"
done

require_dir() {
  local name="$1"
  if [[ ! -d "$DEPLOY_DIR/$name" ]] || [[ -z "$(ls -A "$DEPLOY_DIR/$name" 2>/dev/null || true)" ]]; then
    echo "Missing deploy artifact for $name (not built and no previous gh-pages copy)" >&2
    exit 1
  fi
}

find_index_dir() {
  local root="$1"
  local index
  # Prefer the shallowest index.html so a nested copy cannot replace the app root.
  index="$(find "$root" -name index.html -printf '%d\t%p\n' | sort -n | head -1 | cut -f2-)"
  if [[ -z "$index" ]]; then
    echo "Missing index.html under $root" >&2
    exit 1
  fi
  dirname "$index"
}

copy_web_app() {
  local app="$1"
  local artifact="$ARTIFACTS_DIR/web-$app"
  if [[ ! -d "$artifact" ]]; then
    echo "Missing web artifact directory $artifact" >&2
    exit 1
  fi
  local src
  src="$(find_index_dir "$artifact")"
  rm -rf "$DEPLOY_DIR/$app"
  mkdir -p "$DEPLOY_DIR/$app"
  cp -a "$src/." "$DEPLOY_DIR/$app/"
}

copy_android_landing() {
  local app="$1"
  mkdir -p "$DEPLOY_DIR/$app"
  cp -a "$app/site/." "$DEPLOY_DIR/$app/"
  cp android/landing/styles.css "$DEPLOY_DIR/$app/styles.css"
}

copy_android_apk_from_artifact() {
  local app="$1"
  local artifact="$ARTIFACTS_DIR/android-$app"
  local apk
  apk="$(find "$artifact" -name app-release.apk -print -quit)"
  if [[ -z "$apk" ]]; then
    echo "Missing APK artifact for $app" >&2
    exit 1
  fi
  cp "$apk" "$DEPLOY_DIR/$app/$app.apk"
}

web_names="$(jq -r '.[].name' <<<"$WEB_MATRIX")"
android_names="$(jq -r '.[].name' <<<"$ANDROID_MATRIX")"

web_was_built() {
  [[ -n "$web_names" ]] && grep -qx "$1" <<<"$web_names"
}

android_was_built() {
  [[ -n "$android_names" ]] && grep -qx "$1" <<<"$android_names"
}

for app in "${WEB_APPS[@]}"; do
  if web_was_built "$app"; then
    copy_web_app "$app"
  else
    require_dir "$app"
  fi
done

for app in "${ANDROID_APPS[@]}"; do
  if android_was_built "$app"; then
    rm -rf "$DEPLOY_DIR/$app"
    copy_android_landing "$app"
    copy_android_apk_from_artifact "$app"
  elif is_true "$REFRESH_ANDROID_LANDINGS"; then
    copy_android_landing "$app"
    if [[ ! -f "$DEPLOY_DIR/$app/$app.apk" ]]; then
      echo "Missing APK for $app during landing refresh" >&2
      exit 1
    fi
  else
    require_dir "$app"
    if [[ ! -f "$DEPLOY_DIR/$app/$app.apk" ]]; then
      echo "Missing APK for $app" >&2
      exit 1
    fi
  fi
done

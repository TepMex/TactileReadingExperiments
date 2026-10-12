#!/usr/bin/env bash
# Assembles a site from a fake previous tree and fake build artifacts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export CI_APPS_SH="$ROOT/scripts/ci-apps.fixture.sh"
# shellcheck source=ci-apps.fixture.sh
source "$CI_APPS_SH"

TMP="$(mktemp -d)"
created=()
cleanup() {
  rm -rf "$TMP"
  local path
  for path in "${created[@]}"; do
    rm -rf "$path"
  done
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

reserve() {
  local path="$1"
  if [[ -e "$path" ]]; then
    fail "refusing to overwrite existing $path"
  fi
  created+=("$path")
}

write_landing() {
  local app="$1"
  local dir="$ROOT/$app"
  reserve "$dir"
  mkdir -p "$dir/site"
  cat > "$dir/site/index.html" <<EOF
<!DOCTYPE html>
<html>
<body>
<main>
  <a class="download" href="${app}.apk">Download</a>
  <!-- deploy-footer -->
  <!-- deploy-footer -->
</main>
</body>
</html>
EOF
}

for app in "${ANDROID_APPS[@]}"; do
  write_landing "$app"
done
reserve "$ROOT/fixture-static.html"
echo "static" > "$ROOT/fixture-static.html"

previous="$TMP/previous"
mkdir -p "$previous"
touch "$previous/.nojekyll"
echo kept > "$previous/.nojekyll"
for app in "${WEB_APPS[@]}"; do
  mkdir -p "$previous/$app"
  echo old > "$previous/$app/index.html"
  echo stale > "$previous/$app/stale.js"
done
for app in "${ANDROID_APPS[@]}"; do
  mkdir -p "$previous/$app"
  echo old-apk > "$previous/$app/$app.apk"
  echo old-html > "$previous/$app/index.html"
done
mkdir -p "$previous/.git"
echo secret > "$previous/.git/config"

artifacts="$TMP/artifacts"
mkdir -p "$artifacts/web-alpha/alpha/dist/nested"
echo '<html>new alpha</html>' > "$artifacts/web-alpha/alpha/dist/index.html"
echo '<html>decoy</html>' > "$artifacts/web-alpha/alpha/dist/nested/index.html"
mkdir -p "$artifacts/android-pair-android/nested"
echo new-apk > "$artifacts/android-pair-android/nested/app-release.apk"

deploy="$TMP/deploy"
HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=false \
  WEB_MATRIX='[{"name":"alpha","runtime":"bun"}]' \
  ANDROID_MATRIX='[{"name":"pair-android","runtime":"bun"}]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$deploy" \
  CI_APPS_SH="$CI_APPS_SH" \
  bash "$ROOT/scripts/ci-assemble-site.sh"

[[ "$(cat "$deploy/alpha/index.html")" == "<html>new alpha</html>" ]] || fail "alpha was not replaced"
[[ ! -f "$deploy/alpha/stale.js" ]] || fail "stale web asset survived"
[[ "$(cat "$deploy/beta/index.html")" == "old" ]] || fail "unchanged web app was not kept"
[[ "$(cat "$deploy/pair-android/pair-android.apk")" == "new-apk" ]] || fail "apk was not copied"
[[ -f "$deploy/pair-android/index.html" ]] || fail "landing html missing"
cmp -s android/landing/styles.css "$deploy/pair-android/styles.css" || fail "shared landing css was not applied"
grep -q "deploy-footer" "$deploy/pair-android/index.html" || fail "landing markers missing"
[[ "$(cat "$deploy/kept-android/kept-android.apk")" == "old-apk" ]] || fail "unchanged apk was not kept"
[[ ! -e "$deploy/.git" ]] || fail "previous .git was copied"
[[ "$(cat "$deploy/fixture-static.html")" == "static" ]] || fail "static root file missing"
[[ -f "$deploy/.nojekyll" ]] || fail "nojekyll missing"

# Landing refresh keeps the previous APK and rewrites HTML.
deploy_refresh="$TMP/deploy-refresh"
HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=true \
  WEB_MATRIX='[]' \
  ANDROID_MATRIX='[]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$deploy_refresh" \
  CI_APPS_SH="$CI_APPS_SH" \
  bash "$ROOT/scripts/ci-assemble-site.sh"
[[ "$(cat "$deploy_refresh/notes-app/notes-app.apk")" == "old-apk" ]] || fail "refresh dropped apk"
grep -q "notes-app.apk" "$deploy_refresh/notes-app/index.html" || fail "refresh did not copy landing html"

# Refresh without an APK fails.
rm -f "$previous/notes-app/notes-app.apk"
if HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=true \
  WEB_MATRIX='[]' \
  ANDROID_MATRIX='[]' \
  PREVIOUS_SITE="$previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$TMP/deploy-fail" \
  CI_APPS_SH="$CI_APPS_SH" \
  bash "$ROOT/scripts/ci-assemble-site.sh"; then
  fail "refresh with a missing apk should fail"
fi

# Empty production catalog still publishes .nojekyll and skips .git.
empty_previous="$TMP/empty-previous"
mkdir -p "$empty_previous/.git"
echo secret > "$empty_previous/.git/config"
echo note > "$empty_previous/note.txt"
touch "$empty_previous/.nojekyll"
empty_deploy="$TMP/empty-deploy"
HAS_PREVIOUS=true \
  REFRESH_ANDROID_LANDINGS=false \
  WEB_MATRIX='[]' \
  ANDROID_MATRIX='[]' \
  PREVIOUS_SITE="$empty_previous" \
  ARTIFACTS_DIR="$artifacts" \
  DEPLOY_DIR="$empty_deploy" \
  env -u CI_APPS_SH \
  bash "$ROOT/scripts/ci-assemble-site.sh"
[[ -f "$empty_deploy/.nojekyll" ]] || fail "empty catalog dropped nojekyll"
[[ "$(cat "$empty_deploy/note.txt")" == "note" ]] || fail "empty catalog dropped previous file"
[[ ! -e "$empty_deploy/.git" ]] || fail "empty catalog copied .git"

echo "ci-assemble-site tests passed"

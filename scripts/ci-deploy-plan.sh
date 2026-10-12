#!/usr/bin/env bash
# Decide which apps to build for a GitHub Pages deploy.
#
# Selective on purpose: editing this workflow or CI scripts does not rebuild
# every app. Use workflow_dispatch with full_deploy when a build recipe change
# must republish projects whose own folders did not change.
#
# Apps missing from the previous gh-pages tree are built even when their
# folders did not change, so a failed publish of a new app is retried.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=ci-apps.sh
source "${CI_APPS_SH:-$ROOT/scripts/ci-apps.sh}"

EVENT_NAME="${EVENT_NAME:-}"
DIFF_BASE="${DIFF_BASE:-}"
DIFF_HEAD="${DIFF_HEAD:-HEAD}"
FULL_DEPLOY_INPUT="${FULL_DEPLOY_INPUT:-false}"
PREVIOUS_SITE="${PREVIOUS_SITE:-previous-site}"
ZERO_SHA="0000000000000000000000000000000000000000"

is_true() { [[ "${1:-}" == "true" ]]; }

array_contains() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

web_selected=()
android_selected=()

add_web() {
  local app="$1"
  if ((${#web_selected[@]})) && array_contains "$app" "${web_selected[@]}"; then
    return
  fi
  web_selected+=("$app")
}

add_android() {
  local app="$1"
  if ((${#android_selected[@]})) && array_contains "$app" "${android_selected[@]}"; then
    return
  fi
  android_selected+=("$app")
}

select_all() {
  web_selected=("${WEB_APPS[@]}")
  android_selected=("${ANDROID_APPS[@]}")
}

previous_has_web() {
  local app="$1"
  local dir="$PREVIOUS_SITE/$app"
  [[ -d "$dir" ]] || return 1
  [[ -n "$(ls -A "$dir" 2>/dev/null || true)" ]]
}

previous_has_apk() {
  [[ -f "$PREVIOUS_SITE/$1/$1.apk" ]]
}

is_signing_file() {
  local file="$1"
  local candidate
  for candidate in "${SIGNING_FILES[@]}"; do
    [[ "$file" == "$candidate" ]] && return 0
  done
  return 1
}

is_landing_script() {
  local file="$1"
  local candidate
  for candidate in "${LANDING_SCRIPT_FILES[@]}"; do
    [[ "$file" == "$candidate" ]] && return 0
  done
  return 1
}

is_static_root() {
  local file="$1"
  local candidate
  for candidate in "${STATIC_ROOT_FILES[@]}"; do
    [[ "$file" == "$candidate" ]] && return 0
  done
  return 1
}

path_is_app() {
  local app="$1"
  local file="$2"
  [[ "$file" == "$app" || "$file" == "$app/"* ]]
}

matrix_json() {
  local kind="$1"
  shift
  if [[ $# -eq 0 ]]; then
    echo '[]'
    return
  fi
  local items=() app runtime
  for app in "$@"; do
    if [[ "$kind" == web ]]; then
      runtime="$(web_runtime "$app")"
    else
      runtime="$(android_web_runtime "$app")"
    fi
    items+=("$(jq -nc --arg name "$app" --arg runtime "$runtime" '{name:$name,runtime:$runtime}')")
  done
  local IFS=','
  echo "[${items[*]}]"
}

has_previous=false
if [[ -d "$PREVIOUS_SITE" ]]; then
  if [[ -f "$PREVIOUS_SITE/.nojekyll" ]]; then
    has_previous=true
  else
    for app in "${WEB_APPS[@]}" "${ANDROID_APPS[@]}"; do
      if [[ -d "$PREVIOUS_SITE/$app" ]]; then
        has_previous=true
        break
      fi
    done
  fi
fi

full_deploy=false
if is_true "$FULL_DEPLOY_INPUT"; then
  full_deploy=true
fi
if [[ "$has_previous" == false ]]; then
  full_deploy=true
fi

changed_files=()
static_root_changed=false
refresh_android_landings=false
signing_changed=false

if [[ "$full_deploy" == false ]]; then
  if [[ -n "${CHANGED_FILES_FILE:-}" ]]; then
    if [[ -s "$CHANGED_FILES_FILE" ]]; then
      mapfile -t changed_files < "$CHANGED_FILES_FILE"
    fi
  elif [[ "$EVENT_NAME" == "workflow_dispatch" ]]; then
    changed_files=()
  elif [[ -z "$DIFF_BASE" || "$DIFF_BASE" == "$ZERO_SHA" ]]; then
    echo "No diff base for this push; building every app."
    full_deploy=true
  elif ! git cat-file -e "${DIFF_BASE}^{commit}" 2>/dev/null; then
    echo "Diff base $DIFF_BASE is not available; building every app."
    full_deploy=true
  else
    mapfile -t changed_files < <(git diff --name-only "$DIFF_BASE" "$DIFF_HEAD")
  fi
fi

if [[ "$full_deploy" == true ]]; then
  select_all
  refresh_android_landings=true
else
  if ((${#changed_files[@]})); then
  for file in "${changed_files[@]}"; do
    [[ -n "$file" ]] || continue
    if is_signing_file "$file"; then
      signing_changed=true
    fi
    if is_landing_script "$file" || [[ "$file" == android/landing || "$file" == android/landing/* ]]; then
      refresh_android_landings=true
    fi
    if is_static_root "$file"; then
      static_root_changed=true
    fi
    for app in "${WEB_APPS[@]}"; do
      if path_is_app "$app" "$file"; then
        add_web "$app"
        paired="$(android_for_web "$app")"
        if [[ -n "$paired" ]]; then
          add_android "$paired"
        fi
      fi
    done
    for app in "${ANDROID_APPS[@]}"; do
      if path_is_app "$app" "$file"; then
        add_android "$app"
      fi
    done
  done
  fi

  if [[ "$signing_changed" == true ]]; then
    android_selected=("${ANDROID_APPS[@]}")
  fi

  # Retry apps that never made it onto gh-pages (for example a failed deploy).
  for app in "${WEB_APPS[@]}"; do
    if ! previous_has_web "$app"; then
      echo "Previous site is missing web app $app; scheduling a build."
      add_web "$app"
    fi
  done
  for app in "${ANDROID_APPS[@]}"; do
    if ! previous_has_apk "$app"; then
      echo "Previous site is missing APK $app; scheduling a build."
      add_android "$app"
    fi
  done
fi

# Keep catalog order so logs and matrices stay stable.
ordered_web=()
for app in "${WEB_APPS[@]}"; do
  if ((${#web_selected[@]})) && array_contains "$app" "${web_selected[@]}"; then
    ordered_web+=("$app")
  fi
done
web_selected=()
if ((${#ordered_web[@]})); then
  web_selected=("${ordered_web[@]}")
fi

ordered_android=()
for app in "${ANDROID_APPS[@]}"; do
  if ((${#android_selected[@]})) && array_contains "$app" "${android_selected[@]}"; then
    ordered_android+=("$app")
  fi
done
android_selected=()
if ((${#ordered_android[@]})); then
  android_selected=("${ordered_android[@]}")
fi

should_deploy=false
if ((${#web_selected[@]})) || ((${#android_selected[@]})); then
  should_deploy=true
fi
if [[ "$refresh_android_landings" == true || "$static_root_changed" == true ]]; then
  should_deploy=true
fi

if ((${#web_selected[@]})); then
  web_matrix="$(matrix_json web "${web_selected[@]}")"
else
  web_matrix="[]"
fi
if ((${#android_selected[@]})); then
  android_matrix="$(matrix_json android "${android_selected[@]}")"
else
  android_matrix="[]"
fi

echo "$web_matrix" | jq -e . >/dev/null
echo "$android_matrix" | jq -e . >/dev/null

deployed_at="${DEPLOYED_AT:-$(date -u +"%Y-%m-%dT%H:%M:%S UTC")}"

echo "Deploy plan: full_deploy=$full_deploy should_deploy=$should_deploy web=${#web_selected[@]} android=${#android_selected[@]} refresh_landings=$refresh_android_landings static_root=$static_root_changed"
if ((${#web_selected[@]})); then
  echo "Web builds: ${web_selected[*]}"
fi
if ((${#android_selected[@]})); then
  echo "Android builds: ${android_selected[*]}"
fi

{
  echo "has_previous=$has_previous"
  echo "full_deploy=$full_deploy"
  echo "should_deploy=$should_deploy"
  echo "refresh_android_landings=$refresh_android_landings"
  echo "static_root_changed=$static_root_changed"
  echo "deployed_at=$deployed_at"
  echo "web_count=${#web_selected[@]}"
  echo "android_count=${#android_selected[@]}"
  echo "web_matrix=$web_matrix"
  echo "android_matrix=$android_matrix"
} >> "${GITHUB_OUTPUT:-/dev/stdout}"

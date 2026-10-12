#!/usr/bin/env bash
# Inject deploy footers into Android landing pages that were republished.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=ci-apps.sh
source "${CI_APPS_SH:-$ROOT/scripts/ci-apps.sh}"

REFRESH_ANDROID_LANDINGS="${REFRESH_ANDROID_LANDINGS:-false}"
ANDROID_MATRIX="${ANDROID_MATRIX:-[]}"
DEPLOY_DIR="${DEPLOY_DIR:-deploy}"

is_true() { [[ "${1:-}" == "true" ]]; }

pages=()
if is_true "$REFRESH_ANDROID_LANDINGS"; then
  for app in "${ANDROID_APPS[@]}"; do
    pages+=("$DEPLOY_DIR/$app/index.html")
  done
else
  while IFS= read -r app; do
    [[ -n "$app" ]] || continue
    pages+=("$DEPLOY_DIR/$app/index.html")
  done < <(jq -r '.[].name' <<<"$ANDROID_MATRIX")
fi

if [[ ${#pages[@]} -eq 0 ]]; then
  echo "No Android landing pages to update"
  exit 0
fi

bun run scripts/inject-static-site-footer.ts "${pages[@]}"

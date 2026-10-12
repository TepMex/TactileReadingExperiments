#!/usr/bin/env bash
# Build one web app for GitHub Pages. Run from the repository root.
set -euo pipefail

app="${1:?Usage: ci-build-web.sh <app>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

repo_name="${GITHUB_REPOSITORY_NAME:-}"
if [[ -z "$repo_name" && -n "${GITHUB_REPOSITORY:-}" ]]; then
  repo_name="${GITHUB_REPOSITORY##*/}"
fi
repo_name="${repo_name:-TactileReadingExperiments}"
export GH_PAGES_PUBLIC_PATH="/${repo_name}/${app}/"

cd "$app"
# Default recipe is Bun. An npm app (or one that needs a pre-step) gets a
# branch here and a matching web_runtime in scripts/ci-apps.sh.
bun install --frozen-lockfile
bun run build

if [[ ! -f dist/index.html ]]; then
  echo "Build failed: $app/dist/index.html is missing" >&2
  exit 1
fi

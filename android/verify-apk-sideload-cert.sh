#!/usr/bin/env bash
# Verify release APK is signed with the committed sideload certificate.
set -euo pipefail

apk_path="${1:?Usage: verify-apk-sideload-cert.sh <path-to.apk>}"
expected_file="$(dirname "$0")/expected-sideload-cert-sha256.txt"
expected="$(tr -d '[:space:]' < "$expected_file" | tr '[:upper:]' '[:lower:]')"

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" || ! -d "$sdk/build-tools" ]]; then
    return 1
  fi
  local candidate
  candidate="$(
    find "$sdk/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null \
      | sort -V \
      | tail -1
  )"
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    echo "$candidate"
    return 0
  fi
  return 1
}

apksigner_bin="$(find_apksigner || true)"
if [[ -z "$apksigner_bin" ]]; then
  echo "apksigner not found on PATH or under ANDROID_HOME/ANDROID_SDK_ROOT build-tools" >&2
  exit 1
fi

fingerprint="$(
  "$apksigner_bin" verify --print-certs "$apk_path" 2>/dev/null \
    | grep -m1 'SHA-256 digest:' \
    | sed -E 's/.*SHA-256 digest: //; s/[^0-9a-fA-F]//g' \
    | tr '[:upper:]' '[:lower:]'
)"

if [[ -z "$fingerprint" ]]; then
  echo "Could not read SHA-256 from apksigner for $apk_path" >&2
  exit 1
fi

if [[ "$fingerprint" != "$expected" ]]; then
  echo "APK signing cert mismatch for $apk_path" >&2
  echo "  expected: $expected" >&2
  echo "  actual:   $fingerprint" >&2
  exit 1
fi

echo "OK: $apk_path signed with sideload cert ($fingerprint)"

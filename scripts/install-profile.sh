#!/usr/bin/env bash
# Build the optimized, debug-signed profile APK, install it, and launch it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

source "${ROOT}/scripts/lib/ensure-java-17.sh"
ensure_java_17

if ! command -v adb >/dev/null 2>&1; then
  echo "install-profile: adb not found. Install Android SDK platform-tools and add them to PATH." >&2
  exit 1
fi

if ! adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
  echo "install-profile: no Android device or emulator connected." >&2
  exit 1
fi

./scripts/gradle.sh :app:installProfile
adb shell am start -n com.eskerra.go/.MainActivity

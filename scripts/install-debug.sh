#!/usr/bin/env bash
# Build the debug APK, install it on a connected device/emulator, and launch the app.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/ensure-java-17.sh
source "${ROOT}/scripts/lib/ensure-java-17.sh"
ensure_java_17

if ! command -v adb >/dev/null 2>&1; then
  echo "install-debug: adb not found. Install Android SDK platform-tools and add them to PATH." >&2
  exit 1
fi

if ! adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
  echo "install-debug: no Android device or emulator connected." >&2
  echo "Connect a phone with USB debugging enabled, or start an emulator, then retry." >&2
  exit 1
fi

./scripts/gradle.sh :app:installDebug

adb shell am start -n com.eskerra.go/.MainActivity

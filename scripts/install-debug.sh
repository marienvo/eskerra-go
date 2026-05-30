#!/usr/bin/env bash
# Build the debug APK, install it on a connected device/emulator, and launch the app.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    /usr/lib/jvm/java-17-temurin-jdk \
    /usr/lib/jvm/temurin-17-jdk \
    /usr/lib/jvm/java-17-openjdk; do
    if [ -d "$candidate" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -d "$JAVA_HOME" ]; then
  echo "install-debug: set JAVA_HOME to JDK 17 before running this script." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "install-debug: adb not found. Install Android SDK platform-tools and add them to PATH." >&2
  exit 1
fi

if ! adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
  echo "install-debug: no Android device or emulator connected." >&2
  echo "Connect a phone with USB debugging enabled, or start an emulator, then retry." >&2
  exit 1
fi

./gradlew :app:installDebug

adb shell am start -n com.eskerra.go/.MainActivity

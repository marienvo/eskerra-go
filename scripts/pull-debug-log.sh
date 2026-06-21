#!/usr/bin/env bash
# Pull session 406fc4 debug logs from a connected device into the workspace log file.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.cursor/debug-406fc4.log"
PKG="com.eskerra.go"
DEVICE_LOG="files/debug-406fc4.log"

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device connected." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
: >"$OUT"

if adb exec-out run-as "$PKG" cat "$DEVICE_LOG" >>"$OUT" 2>/dev/null; then
  if [[ -s "$OUT" ]]; then
    echo "Pulled device log to $OUT ($(wc -l <"$OUT") lines)"
    exit 0
  fi
fi

echo "Device log file empty or unavailable; falling back to logcat tag Debug406fc4" >&2
adb logcat -d -s Debug406fc4 | sed -n 's/^.*Debug406fc4: //p' >>"$OUT" || true

if [[ -s "$OUT" ]]; then
  echo "Captured logcat output to $OUT ($(wc -l <"$OUT") lines)"
else
  echo "No debug logs found. Install the instrumented debug build and reproduce the sync issue." >&2
  exit 1
fi

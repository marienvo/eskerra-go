#!/usr/bin/env bash
# Pull a debug session NDJSON log from a connected device into .cursor/debug-<session>.log.
# Usage: ./scripts/pull-debug-log.sh <session-id>
set -euo pipefail

usage() {
  echo "Usage: $(basename "$0") <session-id>" >&2
  echo "Example: $(basename "$0") 406fc4" >&2
  exit 1
}

if [[ $# -ne 1 ]]; then
  usage
fi

SESSION_ID="$1"
if [[ ! "$SESSION_ID" =~ ^[0-9a-fA-F]{4,16}$ ]]; then
  echo "Invalid session id: $SESSION_ID (expected 4-16 hex characters)" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.cursor/debug-${SESSION_ID}.log"
PKG="com.eskerra.go"
DEVICE_LOG="files/debug-${SESSION_ID}.log"
LOGCAT_TAG="Debug${SESSION_ID}"

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

echo "Device log file empty or unavailable; falling back to logcat tag $LOGCAT_TAG" >&2
adb logcat -d -s "$LOGCAT_TAG" | sed -n "s/^.*${LOGCAT_TAG}: //p" >>"$OUT" || true

if [[ -s "$OUT" ]]; then
  echo "Captured logcat output to $OUT ($(wc -l <"$OUT") lines)"
else
  echo "No debug logs found. Install the instrumented debug build and reproduce the issue." >&2
  exit 1
fi

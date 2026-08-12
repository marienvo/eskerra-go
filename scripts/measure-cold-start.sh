#!/usr/bin/env bash
# Measure cold-start launch-settled and TotalTime, following the procedure in
# specs/performance/cold-start-debug-logbook.md.
#
# Requires the temporary BootTrace instrumentation to be present in the installed build.
# Discards runs that do not report LaunchState: COLD, or that produce no launch-settled mark.
#
# Usage: scripts/measure-cold-start.sh [runs] [label]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RUNS="${1:-7}"
LABEL="${2:-unlabelled}"
PACKAGE="com.eskerra.go"
ACTIVITY="${PACKAGE}/.MainActivity"
MAX_DISCARDS=30

if ! command -v adb >/dev/null 2>&1; then
  echo "measure-cold-start: adb not found. Install Android SDK platform-tools and add them to PATH." >&2
  exit 1
fi

if ! adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit !found }'; then
  echo "measure-cold-start: no Android device or emulator connected." >&2
  exit 1
fi

OUT_DIR="$ROOT/.cursor"
mkdir -p "$OUT_DIR"
TRACE_LOG="$OUT_DIR/cold-start-${LABEL}.log"
: >"$TRACE_LOG"

settled_times=()
total_times=()
discards=0
run=0

echo "measure-cold-start: label=${LABEL} target=${RUNS} good runs"
echo

while [ "${#settled_times[@]}" -lt "$RUNS" ]; do
  if [ "$discards" -ge "$MAX_DISCARDS" ]; then
    echo "measure-cold-start: gave up after ${discards} discarded runs." >&2
    exit 1
  fi
  run=$((run + 1))

  adb shell am force-stop "$PACKAGE" >/dev/null
  # Wait for the process to actually be gone so the next start is genuinely cold.
  for _ in $(seq 1 40); do
    if [ -z "$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')" ]; then
      break
    fi
    sleep 0.25
  done
  sleep 2
  adb logcat -c >/dev/null 2>&1 || true

  start_output="$(adb shell am start -W -n "$ACTIVITY" 2>&1 | tr -d '\r')"
  launch_state="$(printf '%s\n' "$start_output" | awk -F': *' '/LaunchState:/ { print $2 }')"
  total="$(printf '%s\n' "$start_output" | awk -F': *' '/^TotalTime:/ { print $2 }')"

  # Give the app room to reach launch-settled before scraping the trace.
  sleep 6
  trace="$(adb logcat -d -s BootTrace 2>/dev/null | tr -d '\r' || true)"
  settled="$(printf '%s\n' "$trace" | sed -n 's/.*[^0-9]\([0-9][0-9]*\)ms launch-settled.*/\1/p' | tail -1)"

  if [ "$launch_state" != "COLD" ] || [ -z "$settled" ]; then
    discards=$((discards + 1))
    echo "run ${run}: discarded (LaunchState=${launch_state:-none} settled=${settled:-none})"
    continue
  fi

  settled_times+=("$settled")
  total_times+=("$total")
  echo "run ${run}: settled=${settled}ms total=${total}ms"
  {
    echo "=== run ${run} (label=${LABEL}) settled=${settled}ms total=${total}ms ==="
    printf '%s\n' "$trace"
    echo
  } >>"$TRACE_LOG"
done

median() {
  printf '%s\n' "$@" | sort -n | awk '{ v[NR] = $1 } END {
    if (NR % 2) { print v[(NR + 1) / 2] } else { printf "%.1f\n", (v[NR/2] + v[NR/2 + 1]) / 2 }
  }'
}

echo
echo "measure-cold-start: label=${LABEL} good=${#settled_times[@]} discarded=${discards}"
echo "  launch-settled median: $(median "${settled_times[@]}") ms"
echo "  TotalTime median:      $(median "${total_times[@]}") ms"
echo "  per-run traces:        ${TRACE_LOG}"

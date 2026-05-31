#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/module-budget-update-lib.sh
source "${SCRIPT_DIR}/lib/module-budget-update-lib.sh"

failures=0

assert_eq_json() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if ! diff -u <(echo "$expected" | jq -S .) <(echo "$actual" | jq -S .) >/dev/null; then
    echo "FAIL: ${name}" >&2
    echo "  expected: $(echo "$expected" | jq -c .)" >&2
    echo "  actual:   $(echo "$actual" | jq -c .)" >&2
    failures=$((failures + 1))
  else
    echo "ok: ${name}"
  fi
}

mock_path_exists_all() {
  return 0
}

mock_path_exists_none() {
  return 1
}

mock_count_tiny_or_large() {
  case "$1" in
    *tiny*) echo "$NEW_FILE_MAX_LINES" ;;
    *) echo $((NEW_FILE_MAX_LINES + 1)) ;;
  esac
}

mock_count_zero() {
  echo "mock count_lines_for_path should not run" >&2
  exit 1
}

result="$(
  build_updated_max_lines_by_path \
    $'apps/desktop/src/lib/tiny.ts\t1006\napps/desktop/src/lib/still-large.ts\t1006' \
    mock_path_exists_all \
    mock_count_tiny_or_large \
    ''
)"
assert_eq_json \
  "removes existing baseline entries that no longer exceed the new-file threshold" \
  '{"maxLinesByPath":{"apps/desktop/src/lib/still-large.ts":401}}' \
  "$result"

result="$(
  build_updated_max_lines_by_path \
    '' \
    mock_path_exists_none \
    mock_count_zero \
    $'apps/desktop/src/lib/tiny-new.ts\t400\napps/desktop/src/lib/large-new.ts\t401'
)"
assert_eq_json \
  "ignores auto additions that do not exceed the new-file threshold" \
  '{"maxLinesByPath":{"apps/desktop/src/lib/large-new.ts":401}}' \
  "$result"

result="$(
  build_updated_max_lines_by_path \
    $'apps/desktop/src/lib/removed-from-repo.ts\t1006' \
    mock_path_exists_none \
    mock_count_zero \
    ''
)"
assert_eq_json \
  "removes baseline entries when the file no longer exists on disk" \
  '{"maxLinesByPath":{}}' \
  "$result"

if (( failures > 0 )); then
  echo "${failures} test(s) failed" >&2
  exit 1
fi

echo "All module budget update tests passed."

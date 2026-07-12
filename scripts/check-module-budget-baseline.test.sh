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

assert_contains() {
  local name="$1"
  local haystack="$2"
  local needle="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "ok: ${name}"
  else
    echo "FAIL: ${name}" >&2
    echo "  expected to contain: ${needle}" >&2
    echo "  actual: ${haystack}" >&2
    failures=$((failures + 1))
  fi
}

assert_empty() {
  local name="$1"
  local actual="$2"
  if [[ -z "${actual//[[:space:]]/}" ]]; then
    echo "ok: ${name}"
  else
    echo "FAIL: ${name}" >&2
    echo "  expected no output, got: ${actual}" >&2
    failures=$((failures + 1))
  fi
}

# --- Pure updater (build_updated_max_lines_by_path) --------------------------

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
    $'app/src/main/java/com/eskerra/go/tiny.kt\t1006\napp/src/main/java/com/eskerra/go/still-large.kt\t1006' \
    mock_path_exists_all \
    mock_count_tiny_or_large
)"
assert_eq_json \
  "removes existing baseline entries that no longer exceed the new-file threshold" \
  '{"maxLinesByPath":{"app/src/main/java/com/eskerra/go/still-large.kt":401}}' \
  "$result"

result="$(
  build_updated_max_lines_by_path \
    $'app/src/main/java/com/eskerra/go/removed.kt\t1006' \
    mock_path_exists_none \
    mock_count_zero
)"
assert_eq_json \
  "removes baseline entries when the file no longer exists on disk" \
  '{"maxLinesByPath":{}}' \
  "$result"

# cap-lower-applied: a file that shrank below its cap ratchets the cap down.
mock_count_shrunk() {
  echo 500
}
result="$(
  build_updated_max_lines_by_path \
    $'app/src/main/java/com/eskerra/go/shrunk.kt\t900' \
    mock_path_exists_all \
    mock_count_shrunk
)"
assert_eq_json \
  "cap-lower-applied: cap ratchets down to the shrunk size" \
  '{"maxLinesByPath":{"app/src/main/java/com/eskerra/go/shrunk.kt":500}}' \
  "$result"

# cap-raise-rejected: a file that exceeds its cap keeps the cap, never raises it.
mock_count_grew() {
  echo 950
}
result="$(
  build_updated_max_lines_by_path \
    $'app/src/main/java/com/eskerra/go/grew.kt\t900' \
    mock_path_exists_all \
    mock_count_grew
)"
assert_eq_json \
  "cap-raise-rejected: cap stays at 900 even though the file is 950" \
  '{"maxLinesByPath":{"app/src/main/java/com/eskerra/go/grew.kt":900}}' \
  "$result"

# --- Integration (touch-it-tidy-it) against a throwaway git fixture ----------
#
# collect_git_budget_violations reads REPO_ROOT/BASELINE_PATH baked at source
# time, so each case runs it in a fresh bash process with those pointed at a
# temp git repo. MODULE_BUDGET_MERGE_BASE pins the merge base to the seed commit.

COMMON_LIB="${SCRIPT_DIR}/lib/module-budget-common.sh"
SRC_DIR_REL="app/src/main/java/com/eskerra/go"

make_lines() { # count -> that many "// x" lines
  local n="$1" i
  for ((i = 0; i < n; i++)); do echo "// x"; done
}

git_fixture() { # sets FIXTURE to a fresh temp git repo with a seed commit; echoes seed sha
  FIXTURE="$(mktemp -d)"
  git -C "$FIXTURE" init -q
  git -C "$FIXTURE" config user.email test@example.com
  git -C "$FIXTURE" config user.name Test
  mkdir -p "${FIXTURE}/${SRC_DIR_REL}"
  echo '{"maxLinesByPath":{}}' >"${FIXTURE}/baseline.json"
}

seed_commit() {
  git -C "$FIXTURE" add -A
  git -C "$FIXTURE" commit -q -m seed
  git -C "$FIXTURE" rev-parse HEAD
}

run_check() { # runs collect_git_budget_violations against $FIXTURE
  REPO_ROOT="$FIXTURE" \
  BASELINE_PATH="${FIXTURE}/baseline.json" \
  MODULE_BUDGET_MERGE_BASE="$1" \
    bash -c "source '${COMMON_LIB}'; collect_git_budget_violations" 2>/dev/null
}

# touched-file-grew-past-budget-fails: a <=400 file that crosses 400 when touched.
git_fixture
make_lines 300 >"${FIXTURE}/${SRC_DIR_REL}/Grows.kt"
seed="$(seed_commit)"
make_lines 460 >"${FIXTURE}/${SRC_DIR_REL}/Grows.kt"
out="$(run_check "$seed")"
assert_contains "touched-file-grew-past-budget-fails" "$out" "Grows.kt: grew from 300 to 460"
rm -rf "$FIXTURE"

# touched-file-stays-under-budget-passes: same file edited but kept under 400.
git_fixture
make_lines 300 >"${FIXTURE}/${SRC_DIR_REL}/Edited.kt"
seed="$(seed_commit)"
make_lines 350 >"${FIXTURE}/${SRC_DIR_REL}/Edited.kt"
out="$(run_check "$seed")"
assert_empty "touched-file-under-400-passes" "$out"
rm -rf "$FIXTURE"

# over-400 file touched may only shrink or stay equal.
git_fixture
make_lines 500 >"${FIXTURE}/${SRC_DIR_REL}/Big.kt"
seed="$(seed_commit)"
make_lines 520 >"${FIXTURE}/${SRC_DIR_REL}/Big.kt"
out="$(run_check "$seed")"
assert_contains "touched-over-400-file-grew-fails" "$out" "Big.kt: grew from 500 to 520"
rm -rf "$FIXTURE"

# untouched-large-file-passes: a large file that no diff touches is fine.
git_fixture
make_lines 700 >"${FIXTURE}/${SRC_DIR_REL}/Untouched.kt"
make_lines 100 >"${FIXTURE}/${SRC_DIR_REL}/Touched.kt"
seed="$(seed_commit)"
make_lines 120 >"${FIXTURE}/${SRC_DIR_REL}/Touched.kt"
out="$(run_check "$seed")"
assert_empty "untouched-large-file-passes" "$out"
rm -rf "$FIXTURE"

# new-file-over-400-fails: brand new file with no merge-base counterpart.
git_fixture
make_lines 100 >"${FIXTURE}/${SRC_DIR_REL}/Existing.kt"
seed="$(seed_commit)"
make_lines 450 >"${FIXTURE}/${SRC_DIR_REL}/BrandNew.kt"
git -C "$FIXTURE" add "${SRC_DIR_REL}/BrandNew.kt"
out="$(run_check "$seed")"
assert_contains "new-file-over-400-fails" "$out" "BrandNew.kt: new file has 450 lines"
rm -rf "$FIXTURE"

# rename-inherits-old-path-budget: a moved 600-line file keeps its 600 budget.
git_fixture
make_lines 600 >"${FIXTURE}/${SRC_DIR_REL}/OldName.kt"
seed="$(seed_commit)"
git -C "$FIXTURE" mv "${SRC_DIR_REL}/OldName.kt" "${SRC_DIR_REL}/NewName.kt"
out="$(run_check "$seed")"
assert_empty "rename-inherits-old-path-budget-passes-when-equal" "$out"
# ...but growing past the inherited budget after the rename fails.
make_lines 640 >"${FIXTURE}/${SRC_DIR_REL}/NewName.kt"
out="$(run_check "$seed")"
assert_contains "rename-then-grow-fails" "$out" "NewName.kt: renamed from ${SRC_DIR_REL}/OldName.kt"
rm -rf "$FIXTURE"

if (( failures > 0 )); then
  echo "${failures} test(s) failed" >&2
  exit 1
fi

echo "All module budget update tests passed."

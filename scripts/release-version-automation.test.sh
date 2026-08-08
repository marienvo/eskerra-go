#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUMP_SCRIPT="${SCRIPT_DIR}/bump-release-version.sh"
RESOLVER_SCRIPT="${SCRIPT_DIR}/release-version-automation-resolve-pr.sh"
failures=0

assert_eq() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "ok: ${name}"
  else
    echo "FAIL: ${name}" >&2
    echo "  expected: ${expected}" >&2
    echo "  actual:   ${actual}" >&2
    failures=$((failures + 1))
  fi
}

assert_fails() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "FAIL: ${name}" >&2
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
    failures=$((failures + 1))
  fi
}

make_fixture() {
  FIXTURE="$(mktemp -d)"
  mkdir -p "${FIXTURE}/app"
  git -C "$FIXTURE" init -q
  git -C "$FIXTURE" config user.email test@example.com
  git -C "$FIXTURE" config user.name Test
  git -C "$FIXTURE" config commit.gpgsign false
  printf '%s\n' \
    'android {' \
    '    defaultConfig {' \
    '        versionCode = 41' \
    '        versionName = "0.1.7"' \
    '    }' \
    '}' >"${FIXTURE}/app/build.gradle.kts"
  git -C "$FIXTURE" add app/build.gradle.kts
  git -C "$FIXTURE" commit -q -m seed
}

cleanup_fixture() {
  rm -rf "$FIXTURE"
}

make_fixture
ESKERRA_RELEASE_ROOT_FOR_TESTS="$FIXTURE" "$BUMP_SCRIPT" --kind minor >/dev/null
assert_eq \
  "minor resets patch and increments versionCode" \
  $'        versionCode = 42\n        versionName = "0.2.0"' \
  "$(grep -E 'version(Code|Name)' "${FIXTURE}/app/build.gradle.kts")"
assert_eq \
  "bump changes only the canonical Gradle file" \
  "app/build.gradle.kts" \
  "$(git -C "$FIXTURE" diff --name-only)"
assert_fails \
  "dirty retry is rejected" \
  env ESKERRA_RELEASE_ROOT_FOR_TESTS="$FIXTURE" "$BUMP_SCRIPT" --kind minor
cleanup_fixture

make_fixture
ESKERRA_RELEASE_ROOT_FOR_TESTS="$FIXTURE" "$BUMP_SCRIPT" --kind patch >/dev/null
assert_eq \
  "patch preserves minor and increments versionCode" \
  $'        versionCode = 42\n        versionName = "0.1.8"' \
  "$(grep -E 'version(Code|Name)' "${FIXTURE}/app/build.gradle.kts")"
cleanup_fixture

make_fixture
assert_fails \
  "major bumps are rejected" \
  env ESKERRA_RELEASE_ROOT_FOR_TESTS="$FIXTURE" "$BUMP_SCRIPT" --kind major
assert_eq \
  "rejected kind writes nothing" \
  "" \
  "$(git -C "$FIXTURE" diff --name-only)"
cleanup_fixture

normal_pr='[{"number":36,"merged_at":"2026-08-08T10:00:00Z","base":{"ref":"main"},"head":{"ref":"feature/example"},"labels":[]}]'
assert_eq \
  "normal PR selects minor" \
  '{"sourcePrNumber":36,"releaseKind":"minor"}' \
  "$("$RESOLVER_SCRIPT" - <<<"$normal_pr")"

hotfix_pr="$(jq -c '.[0].labels = [{"name":"version:hotfix"}] | .' <<<"$normal_pr")"
assert_eq \
  "hotfix label selects patch" \
  '{"sourcePrNumber":36,"releaseKind":"patch"}' \
  "$("$RESOLVER_SCRIPT" - <<<"$hotfix_pr")"

none_pr="$(jq -c '.[0].labels = [{"name":"version:none"}] | .' <<<"$normal_pr")"
assert_eq \
  "none label suppresses release" \
  'null' \
  "$("$RESOLVER_SCRIPT" - <<<"$none_pr")"

automation_pr="$(jq -c '.[0].head.ref = "automation/release-pr-35" | .' <<<"$normal_pr")"
assert_eq \
  "automation PR suppresses release recursion" \
  'null' \
  "$("$RESOLVER_SCRIPT" - <<<"$automation_pr")"

assert_eq \
  "direct push suppresses release" \
  'null' \
  "$("$RESOLVER_SCRIPT" - <<<'[]')"

conflicting_pr="$(jq -c '.[0].labels = ["version:hotfix", "version:none"] | .' <<<"$normal_pr")"
assert_fails \
  "conflicting labels fail safe" \
  "$RESOLVER_SCRIPT" - <<<"$conflicting_pr"

ambiguous_pr="$(jq -c '. + [.[0] | .number = 37 | .head.ref = "feature/other"]' <<<"$normal_pr")"
assert_fails \
  "ambiguous source PRs fail safe" \
  "$RESOLVER_SCRIPT" - <<<"$ambiguous_pr"

android_ci="$(<"${SCRIPT_DIR}/../.github/workflows/android-ci.yml")"
if [[ "$android_ci" == *"bump-release-version.sh"* ]]; then
  echo "FAIL: Android CI must never invoke the version writer" >&2
  failures=$((failures + 1))
else
  echo "ok: Android CI never invokes the version writer"
fi
assert_contains \
  "Android CI protects the release automation tests" \
  "$android_ci" \
  './scripts/release-version-automation.test.sh'

release_workflow="$(<"${SCRIPT_DIR}/../.github/workflows/release-version-after-merge.yml")"
assert_contains \
  "post-CI workflow owns the explicit version write" \
  "$release_workflow" \
  './scripts/bump-release-version.sh --kind "$RELEASE_KIND"'

if ((failures > 0)); then
  echo "${failures} test(s) failed" >&2
  exit 1
fi

echo "All release version automation tests passed."

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${ESKERRA_RELEASE_ROOT_FOR_TESTS:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
VERSION_FILE="${REPO_ROOT}/app/build.gradle.kts"

usage() {
  echo "Usage: $0 --kind minor|patch" >&2
  exit 2
}

if [[ "$#" -ne 2 || "$1" != "--kind" ]]; then
  usage
fi

kind="$2"
if [[ "$kind" != "minor" && "$kind" != "patch" ]]; then
  echo "Unsupported release kind: ${kind}. Automatic major bumps are forbidden." >&2
  exit 2
fi

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Missing Android version source: ${VERSION_FILE}" >&2
  exit 1
fi

if [[ -n "$(git -C "$REPO_ROOT" status --porcelain -- app/build.gradle.kts)" ]]; then
  echo "Refusing to bump a dirty app/build.gradle.kts" >&2
  exit 1
fi

version_name_count="$(grep -Ec '^[[:space:]]*versionName = "[0-9]+\.[0-9]+\.[0-9]+"[[:space:]]*$' "$VERSION_FILE")"
version_code_count="$(grep -Ec '^[[:space:]]*versionCode = [0-9]+[[:space:]]*$' "$VERSION_FILE")"
if [[ "$version_name_count" -ne 1 || "$version_code_count" -ne 1 ]]; then
  echo "Expected exactly one numeric versionName and versionCode in app/build.gradle.kts" >&2
  exit 1
fi

version_name="$(sed -nE 's/^[[:space:]]*versionName = "([0-9]+\.[0-9]+\.[0-9]+)"[[:space:]]*$/\1/p' "$VERSION_FILE")"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)[[:space:]]*$/\1/p' "$VERSION_FILE")"
IFS=. read -r major minor patch <<<"$version_name"

case "$kind" in
  minor)
    next_version_name="${major}.$((10#$minor + 1)).0"
    ;;
  patch)
    next_version_name="${major}.${minor}.$((10#$patch + 1))"
    ;;
esac
next_version_code="$((10#$version_code + 1))"

tmp_file="$(mktemp "${VERSION_FILE}.tmp.XXXXXX")"
cleanup() {
  rm -f "$tmp_file"
}
trap cleanup EXIT

awk \
  -v next_name="$next_version_name" \
  -v next_code="$next_version_code" '
    /^[[:space:]]*versionCode = [0-9]+[[:space:]]*$/ {
      sub(/versionCode = [0-9]+/, "versionCode = " next_code)
    }
    /^[[:space:]]*versionName = "[0-9]+\.[0-9]+\.[0-9]+"[[:space:]]*$/ {
      sub(/versionName = "[0-9]+\.[0-9]+\.[0-9]+"/, "versionName = \"" next_name "\"")
    }
    { print }
  ' "$VERSION_FILE" >"$tmp_file"
chmod --reference="$VERSION_FILE" "$tmp_file"
mv "$tmp_file" "$VERSION_FILE"
trap - EXIT

echo "Bumped Android version ${version_name}+${version_code} -> ${next_version_name}+${next_version_code}"

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/ensure-java-17.sh
source "${ROOT}/scripts/lib/ensure-java-17.sh"
ensure_java_17
cd "$ROOT"
exec ./gradlew "$@"

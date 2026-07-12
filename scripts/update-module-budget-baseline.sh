#!/usr/bin/env bash
# Updates scripts/module-budget-baseline.json caps to match current line counts on disk.
# Also appends baseline entries for changed/new files that would fail check-module-budgets
# without an explicit cap (same rules as collect_auto_baseline_additions).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/module-budget-common.sh
source "${SCRIPT_DIR}/lib/module-budget-common.sh"
# shellcheck source=lib/module-budget-update-lib.sh
source "${SCRIPT_DIR}/lib/module-budget-update-lib.sh"

require_jq

path_exists() {
  [[ -f "${REPO_ROOT}/$1" ]]
}

count_lines_for_path() {
  count_lines "${REPO_ROOT}/$1"
}

if [[ ! -f "$BASELINE_PATH" ]]; then
  echo '{"maxLinesByPath":{}}' >"$BASELINE_PATH"
fi

baseline_tsv="$(jq -r '.maxLinesByPath // {} | to_entries[] | [.key, (.value|tostring)] | @tsv' "$BASELINE_PATH")"

# build_updated_max_lines_by_path already emits the full ratcheted baseline
# (every surviving entry, lowered where the file shrank, entries dropped where
# the file fell to <=400 or vanished). Write it directly — a recursive merge
# with the old baseline would resurrect the dropped entries.
build_updated_max_lines_by_path "$baseline_tsv" path_exists count_lines_for_path >"${BASELINE_PATH}.tmp"
mv "${BASELINE_PATH}.tmp" "$BASELINE_PATH"

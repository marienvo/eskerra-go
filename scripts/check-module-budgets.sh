#!/usr/bin/env bash
# Fails when Kotlin modules grow beyond agreed budgets.
# Uses scripts/module-budget-baseline.json for known megamodules and git for new/growth checks.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/module-budget-common.sh
source "${SCRIPT_DIR}/lib/module-budget-common.sh"

require_jq

if [[ ! -f "$BASELINE_PATH" ]]; then
  echo "[check-module-budgets] Missing baseline: ${BASELINE_PATH}" >&2
  exit 1
fi

errors=()
while IFS= read -r line; do
  [[ -n "$line" ]] && errors+=("$line")
done < <(collect_baseline_cap_violations)

while IFS= read -r line; do
  [[ -n "$line" ]] && errors+=("$line")
done < <(collect_git_budget_violations)

if ((${#errors[@]} > 0)); then
  echo "[check-module-budgets] Failed:" >&2
  printf '%s\n' "${errors[@]}" >&2
  exit 1
fi

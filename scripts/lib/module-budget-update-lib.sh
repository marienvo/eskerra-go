#!/usr/bin/env bash
# Pure update logic for module-budget-baseline.json (testable without git).
set -euo pipefail

_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/module-budget-common.sh
source "${_LIB_DIR}/module-budget-common.sh"

# build_updated_max_lines_by_path baseline_tsv path_exists_fn count_lines_fn auto_additions_tsv
# Emits sorted JSON object: {"maxLinesByPath": {...}}
build_updated_max_lines_by_path() {
  local baseline_tsv="$1"
  local path_exists_fn="$2"
  local count_lines_fn="$3"
  local auto_additions_tsv="$4"

  local -A next=()
  local rel cap current rel_add count_add

  while IFS=$'\t' read -r rel cap; do
    [[ -z "$rel" ]] && continue
    if ! "$path_exists_fn" "$rel"; then
      continue
    fi
    current="$("$count_lines_fn" "$rel")"
    if (( current <= NEW_FILE_MAX_LINES )); then
      continue
    fi
    next["$rel"]="$current"
  done <<<"$baseline_tsv"

  while IFS=$'\t' read -r rel_add count_add; do
    [[ -z "$rel_add" ]] && continue
    if (( count_add > NEW_FILE_MAX_LINES )); then
      next["$rel_add"]="$count_add"
    fi
  done <<<"$auto_additions_tsv"

  if ((${#next[@]} == 0)); then
    echo '{"maxLinesByPath":{}}'
    return
  fi

  local keys=()
  local key
  for key in "${!next[@]}"; do
    keys+=("$key")
  done
  mapfile -t sorted_keys < <(printf '%s\n' "${keys[@]}" | sort)

  local pairs_json='[]'
  for key in "${sorted_keys[@]}"; do
    pairs_json="$(jq -n \
      --argjson arr "$pairs_json" \
      --arg key "$key" \
      --argjson value "${next[$key]}" \
      '$arr + [{key: $key, value: $value}]')"
  done

  jq -n --argjson pairs "$pairs_json" \
    '{maxLinesByPath: (reduce $pairs[] as $item ({}; .[$item.key] = $item.value))}'
}

#!/usr/bin/env bash
# Shared helpers for module budget check/update scripts.

if [[ -z "${MODULE_BUDGET_COMMON_LOADED:-}" ]]; then
  MODULE_BUDGET_COMMON_LOADED=1

  _MODULE_BUDGET_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(cd "${_MODULE_BUDGET_LIB_DIR}/../.." && pwd)"
  BASELINE_PATH="${REPO_ROOT}/scripts/module-budget-baseline.json"

  NEW_FILE_MAX_LINES=400
  GROWTH_TRACK_MIN_LINES=800
fi

require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "[module-budget] jq is required but not installed." >&2
    exit 1
  fi
}

count_lines() {
  local abs_path="$1"
  if [[ ! -f "$abs_path" ]]; then
    echo 0
    return
  fi
  if [[ ! -s "$abs_path" ]]; then
    echo 0
    return
  fi
  wc -l <"$abs_path" | tr -d ' '
}

is_scoped_source() {
  local rel="$1"
  if [[ -z "$rel" || "$rel" != *.kt ]]; then
    return 1
  fi
  case "$rel" in
    app/src/main/java/*|app/src/test/java/*|app/src/androidTest/java/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

git_ok() {
  git -C "$REPO_ROOT" "$@" >/dev/null 2>&1
}

git_out() {
  git -C "$REPO_ROOT" "$@"
}

resolve_merge_base() {
  if [[ -n "${MODULE_BUDGET_MERGE_BASE:-}" ]]; then
    echo "${MODULE_BUDGET_MERGE_BASE}"
    return 0
  fi

  local ref merge_base
  for ref in origin/main origin/master main master; do
    if git_ok rev-parse --verify "$ref"; then
      merge_base="$(git_out merge-base HEAD "$ref" 2>/dev/null || true)"
      if [[ -n "$merge_base" ]]; then
        echo "$merge_base"
        return 0
      fi
    fi
  done

  return 1
}

exists_at_revision() {
  local rev="$1"
  local rel_path="$2"
  git_ok cat-file -e "${rev}:${rel_path}"
}

count_lines_at_revision() {
  local rev="$1"
  local rel_path="$2"
  local raw
  raw="$(git_out show "${rev}:${rel_path}" 2>/dev/null || true)"
  if [[ -z "$raw" ]]; then
    echo 0
    return
  fi
  printf '%s\n' "$raw" | wc -l | tr -d ' '
}

baseline_has_path() {
  local rel="$1"
  local cap
  cap="$(jq -r --arg path "$rel" '.maxLinesByPath[$path] // empty' "$BASELINE_PATH")"
  [[ -n "$cap" ]]
}

collect_changed_paths() {
  local merge_base="$1"
  local -a changed=()
  local line

  if [[ -n "$merge_base" ]]; then
    while IFS= read -r line; do
      [[ -n "$line" ]] && changed+=("$line")
    done < <(git_out diff --name-only "${merge_base}...HEAD" 2>/dev/null || true)
  fi

  while IFS= read -r line; do
    [[ -n "$line" ]] && changed+=("$line")
  done < <(git_out diff --name-only HEAD 2>/dev/null || true)

  while IFS= read -r line; do
    [[ -n "$line" ]] && changed+=("$line")
  done < <(git_out diff --name-only --cached HEAD 2>/dev/null || true)

  if ((${#changed[@]} == 0)); then
    return 0
  fi

  printf '%s\n' "${changed[@]}" | awk '!seen[$0]++'
}

collect_auto_baseline_additions() {
  local merge_base
  merge_base="$(resolve_merge_base 2>/dev/null || true)"
  if [[ -z "$merge_base" ]]; then
    return 0
  fi

  local rel abs current was_new prev
  while IFS= read -r rel; do
    [[ -z "$rel" ]] && continue
    is_scoped_source "$rel" || continue

    abs="${REPO_ROOT}/${rel}"
    [[ -f "$abs" ]] || continue
    baseline_has_path "$rel" && continue

    current="$(count_lines "$abs")"
    if ! exists_at_revision "$merge_base" "$rel"; then
      if (( current > NEW_FILE_MAX_LINES )); then
        printf '%s\t%s\n' "$rel" "$current"
      fi
      continue
    fi

    prev="$(count_lines_at_revision "$merge_base" "$rel")"
    if (( prev >= GROWTH_TRACK_MIN_LINES && current > prev )); then
      printf '%s\t%s\n' "$rel" "$current"
    fi
  done < <(collect_changed_paths "$merge_base")
}

collect_baseline_cap_violations() {
  local rel cap abs lines
  while IFS=$'\t' read -r rel cap; do
    [[ -z "$rel" ]] && continue
    abs="${REPO_ROOT}/${rel}"
    if [[ ! -f "$abs" ]]; then
      echo "Baseline path missing on disk: ${rel}"
      continue
    fi
    lines="$(count_lines "$abs")"
    if (( lines > cap )); then
      echo "${rel}: ${lines} lines exceeds baseline cap ${cap}. Shrink the module or raise the baseline deliberately."
    fi
  done < <(jq -r '.maxLinesByPath // {} | to_entries[] | [.key, (.value|tostring)] | @tsv' "$BASELINE_PATH")
}

collect_git_budget_violations() {
  local merge_base
  merge_base="$(resolve_merge_base 2>/dev/null || true)"
  if [[ -z "$merge_base" ]]; then
    echo "[check-module-budgets] No merge base (no origin/main or main). Skipping git-based new/growth checks." >&2
    return 0
  fi

  local rel abs current was_new prev
  while IFS= read -r rel; do
    [[ -z "$rel" ]] && continue
    is_scoped_source "$rel" || continue

    abs="${REPO_ROOT}/${rel}"
    [[ -f "$abs" ]] || continue
    baseline_has_path "$rel" && continue

    current="$(count_lines "$abs")"
    if ! exists_at_revision "$merge_base" "$rel"; then
      if (( current > NEW_FILE_MAX_LINES )); then
        echo "${rel}: new file has ${current} lines (max ${NEW_FILE_MAX_LINES} without baseline entry). Split or add an explicit baseline bump."
      fi
      continue
    fi

    prev="$(count_lines_at_revision "$merge_base" "$rel")"
    if (( prev >= GROWTH_TRACK_MIN_LINES && current > prev )); then
      echo "${rel}: grew from ${prev} to ${current} lines (files ≥${GROWTH_TRACK_MIN_LINES} lines may not grow without intentional refactor/split)."
    fi
  done < <(collect_changed_paths "$merge_base")
}

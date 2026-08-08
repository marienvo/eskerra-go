#!/usr/bin/env bash
set -euo pipefail

input="${1:--}"
if [[ "$input" == "-" ]]; then
  payload="$(cat)"
else
  payload="$(<"$input")"
fi

if ! jq -e 'type == "array"' >/dev/null <<<"$payload"; then
  echo "Associated pull request payload must be an array" >&2
  exit 1
fi

eligible="$(
  jq -c '[.[] | select(
    (.merged_at | type) == "string" and
    .base.ref == "main" and
    (.head.ref | type) == "string" and
    (.head.ref | startswith("automation/release-pr-") | not)
  )]' <<<"$payload"
)"
count="$(jq 'length' <<<"$eligible")"

if [[ "$count" -eq 0 ]]; then
  echo null
  exit 0
fi
if [[ "$count" -ne 1 ]]; then
  numbers="$(jq -r 'map(.number | tostring) | join(", ")' <<<"$eligible")"
  echo "Expected one eligible merged PR, found: ${numbers}" >&2
  exit 1
fi

pull="$(jq -c '.[0]' <<<"$eligible")"
if ! jq -e '.number | type == "number" and . > 0 and floor == .' >/dev/null <<<"$pull"; then
  echo "Eligible merged PR has no valid number" >&2
  exit 1
fi

labels="$(
  jq -r '.labels[]? | if type == "string" then . else (.name // "") end' <<<"$pull"
)"
has_hotfix=false
has_none=false
while IFS= read -r label; do
  [[ "$label" == "version:hotfix" ]] && has_hotfix=true
  [[ "$label" == "version:none" ]] && has_none=true
done <<<"$labels"

if [[ "$has_hotfix" == true && "$has_none" == true ]]; then
  echo "PR cannot have both version:hotfix and version:none" >&2
  exit 1
fi
if [[ "$has_none" == true ]]; then
  echo null
  exit 0
fi

release_kind=minor
[[ "$has_hotfix" == true ]] && release_kind=patch
jq -cn \
  --argjson sourcePrNumber "$(jq '.number' <<<"$pull")" \
  --arg releaseKind "$release_kind" \
  '{sourcePrNumber: $sourcePrNumber, releaseKind: $releaseKind}'

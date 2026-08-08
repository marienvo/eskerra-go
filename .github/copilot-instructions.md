# Repository instructions

When creating a PR, inspect the complete diff and apply the release-label policy in
`specs/rules/release-versioning.md`. Use no version label for the default minor bump, add
`version:hotfix` for an urgent patch, or add `version:none` only when the whole diff cannot affect
the shipped Android app or release artifacts (for example docs/plans/agent instructions/PR
templates/test-only changes). Never combine the labels; when uncertain, default to minor. Set the
label on the GitHub PR itself rather than only mentioning it in the body.

For generated release PRs whose body contains `Release-Automation: true`, synchronized
`versionName` and `versionCode` edits are expected automation output. Do not flag them as unrelated
or ask for their removal. Only report an incorrect minor/patch choice, a `versionCode` that did not
increment exactly once, an incorrect `Release-For-PR` marker, or a file outside the allowlist in
`specs/rules/release-versioning.md`.

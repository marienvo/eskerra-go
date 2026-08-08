# Release versioning

Status: **binding**. This policy applies to the Android app.

## Version policy

- `versionName` in `app/build.gradle.kts` is the canonical semver source.
- `versionCode` in the same file is the Android monotonic build identifier and increments by exactly
  one with every semver bump.
- Every successfully merged, non-automation PR increments the minor version once unless it carries
  the explicit `version:none` label. A minor bump resets patch to zero.
- A source PR labeled `version:hotfix` increments the patch version instead. Patch versions are
  reserved for hotfixes; automation never increments the major version.
- A source PR labeled `version:none` does not increment either version. Use it only when the entire
  diff cannot affect the shipped Android app or its release artifacts, such as documentation,
  plans, agent instructions, PR templates, or test-only changes. Build, packaging, dependency,
  generated-asset, and product-code changes still require a release. When uncertain, omit the label
  so the default minor bump occurs.
- `version:hotfix` and `version:none` are mutually exclusive. Automation fails rather than guessing
  if both are present.
- Direct pushes to `main`, local commits, repeated builds, and generated release PRs do not increment
  the version.

## Automation flow

Gradle builds and `Android CI` are read-only with respect to tracked files. They never invoke the
bump script.

After `Android CI` succeeds for a `main` merge, `release-version-after-merge.yml` resolves the source
PR from the green commit, serializes with other release jobs, and runs the explicit bump command. It
creates and immediately merges `automation/release-pr-<source-number>` through the normal `main` PR
rule. A merge commit is required because the repository's oldest active main ruleset permits only
that merge method. The generated release commit carries:

```text
Release-Automation: true
Release-For-PR: #<source-number>
Release-Kind: minor|patch
```

`Release-For-PR` is the idempotency key: rerunning a completed source workflow must be a no-op. A
direct push has no associated source PR and is skipped. A source PR with `version:none` follows the
same no-write path. The automation checks out only the latest trusted `main`; it never checks out a
source PR head in its write-enabled `workflow_run` context. GitHub's workflow token does not trigger
another workflow run for the generated merge, while the automation branch prefix independently
prevents recursion.

The explicit writer is:

```bash
./scripts/bump-release-version.sh --kind minor
./scripts/bump-release-version.sh --kind patch
```

It refuses a dirty version source and may modify only `app/build.gradle.kts`.

## PR author responsibility

The author or agent creating a PR must inspect its complete diff and choose exactly one policy:

- normal shipping change: set no version label (the default minor bump);
- urgent hotfix: add `version:hotfix`;
- wholly non-shipping change as defined above: add `version:none`.

Agents must set the selected non-default label on the GitHub PR itself, for example with
`gh pr create --label version:none` or `gh pr edit <number> --add-label version:none`. Merely naming
the choice in the PR body does not affect automation.

## Review rule

A PR whose body contains `Release-Automation: true` is generated release metadata, not unrelated
product churn. Reviewers and AI tools must not request removal of an exact generated bump. Report a
finding only when the bump kind is wrong, `versionCode` did not increment exactly once, the
source-PR marker is wrong, or the diff contains a file other than `app/build.gradle.kts`.

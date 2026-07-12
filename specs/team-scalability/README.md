# Team Scalability Working Spec

Process spec for keeping Kotlin modules small enough for parallel work in **eskerra-go**.

Related documents:

- Agent quality gate: [`AGENTS.md`](../../AGENTS.md)
- Baseline caps: [`scripts/module-budget-baseline.json`](../../scripts/module-budget-baseline.json)
- Checker: [`scripts/check-module-budgets.sh`](../../scripts/check-module-budgets.sh)

## Purpose

The app is still a single Gradle module (`:app`), so merge-conflict risk lives at the **file** level. Module budgets stop megamodules from growing silently and force intentional splits when a file must stay large.

## Current hotspots

In order of merge-conflict risk, highest first:

1. `app/src/main/java/com/eskerra/go/app/App.kt` — navigation host and route composition. Largest production file.
2. Sync test megamodules: `ManualSyncNowTest.kt`, `RemoteSyncSettingsRepositoryTest.kt`, `WorkspaceSetupRepositoryTest.kt`. Large enough that parallel edits are awkward.

Exact LOC values are tracked in [`scripts/module-budget-baseline.json`](../../scripts/module-budget-baseline.json). The baseline is the source of truth; do not duplicate numbers here.

## How enforcement works

Three layers (see [`scripts/lib/module-budget-common.sh`](../../scripts/lib/module-budget-common.sh)):

1. **Baseline caps** — known large files have an explicit `maxLinesByPath` ceiling in the baseline JSON. Baseline-pinned files are governed by that cap, and the cap **only ratchets down** (see principle 3).
2. **New file limit** — new `.kt` files under `app/src/{main,test,androidTest}/java/` without a baseline entry may not exceed **400** lines.
3. **Touch it, tidy it** — any changed `.kt` file (not baseline-pinned) must end no larger than `max(400, its line count at the merge base)`. A file at or under 400 may never cross 400; a file already over 400 may only shrink or stay equal. Renamed/moved files inherit the old path's merge-base size; deleted files are ignored; **untouched files are never checked** by this layer, so a large legacy file only becomes a problem once someone edits it.

CI runs `./scripts/check-module-budgets.sh` on every PR and push to `main`.

## Working principles

1. **Behavior preservation first.** Refactor PRs change structure only unless the PR explicitly documents a behavior change.
2. **Small, reviewable PRs.** If the diff cannot fit in a short review window, split it.
3. **Module budgets only move downward.** After an extraction, lower the cap in the baseline JSON. `update-module-budget-baseline.sh` enforces this — it lowers caps to the current size and drops entries that fell to ≤400, but never raises a cap or adds an entry for a grown file. Pinning a new large file (e.g. a deliberate large extraction) is a manual baseline edit, justified in the commit message. Do not silently raise caps to absorb growth.
4. **New extracted modules stay under 400 LOC.** That keeps them off the megamodule track.
5. **Tests come with the code.** Extracted logic ships with colocated unit tests.

## Commands

```bash
# Daily quality gate (also in CI)
./scripts/check-module-budgets.sh

# After a deliberate split/refactor — refresh baseline caps downward
./scripts/update-module-budget-baseline.sh
git add scripts/module-budget-baseline.json
```

Requires `jq` locally (`dnf install jq` on Fedora).

## What this spec does not cover

- Layer dependency enforcement (ArchUnit, Gradle module splits) — separate concern.
- ESLint suppression baselines — notebox-only tooling, not ported here.

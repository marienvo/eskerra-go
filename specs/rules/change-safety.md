# Change-safety rules — taxonomy, tiers, and work-order format

Status: **binding**.

> Provenance: lightweight adaptation of notebox's ADR-005 change-safety model —
> `specs/rules/change-safety.md` + `specs/rules/agent-protocol.md` @
> `c72b677a6334560c12441631189dec576ad771e4` (2026-07-12) — resized from nine change
> types to five and re-grounded on this repo's Kotlin/Compose + JGit surfaces.
> **Canonical here**; notebox is provenance only and this file is not part of any sync-set.

Every change declares **one** type (G1–G5). The type sets the required context, tests,
reviewer, and whether an agent may drive it end-to-end. A change that spans two types is
two changes — the one exception is a bug fix and its regression test, which are one change
by definition.

## Change taxonomy (G1–G5)

Reviewer legend — **self**: author merges on green CI (solo era); **maintainer**: Marien;
**2nd-model**: a fresh AI session handed the diff + the invariant argument.

| # | Type | Risk | Read first (context packet) | Required tests | Reviewer | AI may drive? |
|---|---|---|---|---|---|---|
| **G1** | **Mechanical move** — file split / `git mv` + import rewrites, zero content edits | Low–Med | Target paths; module-budget note (touched files stay within touch-it-tidy-it) | Full `:app:testDebugUnitTest` green with **no test-body edits** beyond import lines | self; maintainer if a red-tier file moves | **Yes** — ideal agent work |
| **G2** | **Local feature change** — one feature slice, no sync/vault-write surface | Low | [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../adr/001-hybrid-layering-and-feature-slices.md) (placement); the slice's existing files across `feature`/`data`/`core` | Colocated unit test for the changed domain/data behavior (AGENTS.md rule); single-file run named in the report | self | **Yes** |
| **G3** | **Sync / vault-write change** — anything touching `data/git`, `ManualSyncNow`, the podcast sync channels, the shared git mutex, a Markdown/vault write path, or FTS reconcile | **Critical** | [`specs/architecture/sync-hardening-and-recovery.md`](../architecture/sync-hardening-and-recovery.md); the layering ADR; enumerate every write/mutation path the change adds or removes | Tests **in the same change** (hard rule); the affected sync/recovery/mark-as-played suites; a new write path ⇒ a new test proving the write is scoped and safe | maintainer (+ 2nd-model) | **No autonomous execution** — agent *proposes* a diff + invariant argument; human applies and verifies |
| **G4** | **Test-only change** — new tests, splits, fakes, harness | Low | The module's existing test style (behavioral, not snapshot) | The tests themselves green; **zero production diff** (CI-verifiable: no non-test file changed) | self | **Yes** — best first task for a new agent |
| **G5** | **Guardrail / meta change** — module budgets, `module-budget-baseline.json`, ArchUnit rules + violation store, `.github/workflows/`, git hooks, this rules file | High (meta) | The guardrail's own tests (`check-module-budget-baseline.test.sh`, the ArchUnit suite); the ratchet philosophy (baselines only go down) | The guardrail's own colocated tests green | maintainer, always | **Mechanism yes, policy no** — an agent may build the checker; only a human decides what it permits |

A change that cannot answer "which G is this?" in one type is mis-scoped.

## File-access tiers

- **Green** (agent edits freely within the task's allowlist): everything not listed below.
- **Yellow** (edit only when the task explicitly targets them): sync orchestration
  (`core/usecase/ManualSyncNow.kt`, `SyncPodcastChange.kt`, `SyncPodcastVaultRefresh.kt`,
  `SyncPodcastChangesViaVaultSync.kt`) and `app/App.kt` (the navigation host).
- **Red** (propose only — the agent may not apply the edit *unless* the task explicitly
  targets that file or category **and** the human approved that scope up front):
  - `data/git/**` (JGit engine internals: `JGitRemoteSyncRepository`, `GitChangeStager`,
    `GitIndexLockRecovery`, `GitLocalBranchAlignment`, `SyncPathClassifier`, `GitSyncMutex`, …)
  - Markdown / vault write paths: `data/notes/FileNoteWriteRepository.kt`,
    `core/usecase/SaveNote.kt`, `CreateInboxNote.kt`, `DeleteInboxNotes.kt`,
    `WritePlaylist.kt` + `core/playlist/PlaylistMerge.kt`
  - FTS reconcile: `data/search/VaultSearchIndexer.kt`,
    `data/search/VaultSearchWorkspaceWalker.kt`, `data/search/SqliteVaultSearchRepository.kt`
  - Guardrail ratchets: `scripts/module-budget-baseline.json`, `app/archunit_store/**`
  - CI and hooks: `.github/workflows/**`, `.claude/hooks/**`, `scripts/githooks/**`
  - This file and any file under `specs/rules/`.

Red tier and G3 overlap by design: touching a red file is almost always a G3 (or G5)
change, and neither is autonomous agent work.

## Work-order format (delegated task prompt)

Every delegated task states, in order — a field it cannot fill means the task is not ready
to delegate:

```
TYPE: G<1-5> per specs/rules/change-safety.md
GOAL: <one sentence; behavior-preserving? yes/no>
READ FIRST: <the exact spec sections / ADR / ARCHITECTURE files for this type's packet>
FILE ALLOWLIST: <globs the diff may touch — anything else is out of scope>
CAUTION FILES: <yellow/red overlaps; "propose only" or "do not touch">
TESTS THAT DEFINE DONE: <exact ./scripts/gradle.sh invocations that must pass, and which
  NEW tests must exist>
NON-GOALS: <the 2-3 adjacent improvements the agent will be tempted to make>
```

Before the first edit, the agent produces (cheap, in-conversation): the call-site
inventory for every symbol it will move or change (`grep -rn`), a quote of the specific
invariant from the READ FIRST material that constrains the task, and a baseline run of the
DONE tests.

## Report format (commit message / work report)

- Files touched vs. the allowlist — any delta is explained or reverted.
- Before/after line counts of any budgeted file the change touched.
- The DONE test invocations with their results pasted.
- **Invariant argument** for G3 (2–5 sentences): name the sync/vault-write invariant the
  change touches (single git mutex, fail-closed on uncertainty, no unscoped stage, no
  destructive recovery on the podcast channel, byte-preserving writes) and why the diff
  preserves it. A missing or vague argument bounces the change.
- Assertion-change list if any test bodies changed, each with a reason.

## Standing rules

- **No drive-by improvements.** An unused-import cleanup, rename, or comment fix outside
  the allowlist goes in a "noticed, not done" list, not the diff. That list is the
  legitimate outlet for agent helpfulness and doubles as a backlog feed.
- **Ratchet tampering is the one instant-revert offense.** Any edit to
  `module-budget-baseline.json` beyond re-pathing or lowering a cap, any growth of the
  ArchUnit violation store, or any new suppression invalidates the change regardless of
  how good the rest is.
- **Never weaken a test to make a suite pass.** Agents do not delete or soften an assertion
  to get to green; a legitimately obsolete assertion is a report item ("assertion X now
  wrong because Y — confirm before I change it"), not a silent edit.
- **Preserve the fragile specifics.** Do not change the git mutex discipline, the
  fast-forward-only rule on the podcast mark-as-played channel, or the fail-closed recovery
  behavior without a spec update in the same change and G3 review.

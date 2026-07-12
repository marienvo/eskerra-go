# SPEC-parity guardrails — seed quality standards from notebox

Status: **active**. Agreed 2026-07-12 (analysis + Q&A in-session). One sequenced
implementation plan in five commits, executed in the order below; each commit leaves
`main` shippable and is reviewable as an individual diff in ~30 minutes.

## Authority and provenance (binding for this whole plan)

Every document, rule, and skill this plan introduces is **seeded** from the notebox repo
and becomes **canonical in eskerra-go the moment it lands here**.

- Seed source: `notebox` @ commit `c72b677a6334560c12441631189dec576ad771e4` (2026-07-12).
  Each seeded file carries a one-line provenance note naming its notebox source path and
  that commit.
- After seeding, **notebox is provenance only** — not an ongoing authority source. If the
  notebox original evolves, that has no effect here; if the eskerra-go copy evolves, do
  not "correct" it back toward notebox.
- **None of this material joins the sync-set.** `scripts/sync-shared-conventions.sh` (the
  existing notebox→eskerra-go mechanism for `.cursor/rules/{language,quality,specs,testing}.mdc`,
  the named `.cursor/skills/`, `.claude/settings.json`, hooks) keeps working exactly as
  today for the files it already owns; nothing from this plan is added to its manifest.
- Drift between the two repos' copies is accepted by design. If it ever hurts, the remedy
  is a manual drift-check skill — a possible later addition, not part of this plan.

New repo-local skills get the same "repo-local" marker `plan-next-pr` already uses, so a
future sync run cannot clobber them (the sync script only replaces skills it names).

---

## Commit 1 — module-budget ratchet fix + touch-it-tidy-it — DONE

Landed. Durable homes: `scripts/lib/module-budget-common.sh` (touch-it-tidy-it +
rename map), `scripts/lib/module-budget-update-lib.sh` (one-way ratchet),
`scripts/check-module-budget-baseline.test.sh` (tests), `AGENTS.md` §
Module size budget, `specs/team-scalability/README.md`,
`.cursor/rules/project-conventions.mdc`.

## Commit 2 — doc map + AGENTS.md invariants — DONE

Landed. Durable homes: `specs/README.md` (doc map + authority order + lifecycle rule),
`AGENTS.md` § Key invariants (startup sacred path + playlist merge) and § Proposing new
work. `specs/observability/` deferred to commit 5.

## Commit 3 — ArchUnit layer rules

Turn the prose architecture rules into unit tests: add `com.tngtech.archunit:archunit-junit4`
(matching the existing JUnit 4 test setup) as `testImplementation`, with rule classes
under `app/src/test/.../architecture/`. Runs inside `:app:testDebugUnitTest`, so it is
automatically part of the existing quality gate and CI — no new mechanism.

Initial rule set, phrased in package/class terms (each maps to an existing AGENTS.md /
project-conventions rule):

1. Classes in `..feature..` packages do not depend on `java.io..`/`java.nio..` file APIs
   or on `..data.git..`.
2. `org.eclipse.jgit..` is accessed only from `..data.git..`.
3. Classes assignable to `androidx.lifecycle.ViewModel` do not depend on
   `android.content.Context`.
4. Markdown parsing / wiki-link resolution classes (by their actual packages, e.g.
   `..markdown..` / `..wikilink..` — verify the real package names before writing the
   rule) do not reside in `..feature..` packages.

Do not attempt a generic "composables receive state and callbacks only" rule: ArchUnit
has no reliable detectable category for composables absent a dedicated package or naming
convention. That rule stays prose (and review-skill territory) until such a convention
exists.

Procedure: verify each rule against the current codebase first; a rule that already
passes lands as-is, a rule with a small number of violations is either fixed trivially in
the same commit or landed frozen (ArchUnit `FreezingArchRule`, violation store committed —
same ratchet philosophy as the module budgets: the store may only shrink). Rules that
would need real refactoring to pass are noted in the work report and left out; they enter
as frozen rules in a follow-up.

## Commit 4 — change-safety rules + review skills

Lightweight adaptation of notebox's ADR-005 model (seed: notebox
`specs/rules/change-safety.md` + `specs/rules/agent-protocol.md`), sized for this repo.

1. **`specs/rules/change-safety.md`** — binding. Small taxonomy (~5 types, one declared
   per commit):
   - **G1 mechanical move** (splits/`git mv`, zero content edits) — agent-ideal; full gate.
   - **G2 local feature change** (one slice, no sync/vault-write surface) — slice unit
     test required.
   - **G3 sync / vault-write change** (`data/git`, `ManualSyncNow`, recovery, conflict
     sidecars, the git mutex, any markdown write path, FTS reconcile) — **critical**:
     tests in the same change, agent proposes / human applies.
   - **G4 test-only change** — zero production diff.
   - **G5 guardrail/meta change** (budgets, baseline JSON, ArchUnit rules, CI, hooks,
     this file) — human decides the rule, agent may build the mechanism.
2. **File-access tiers** in the same doc: green (default), yellow (edit only when the
   task targets them: sync orchestration, `App.kt` navigation host), **red**: propose
   only, *unless* the task explicitly targets that file or category and the human
   approved that scope up front. Red covers: `data/git` engine internals, markdown save
   paths, `scripts/module-budget-baseline.json`, ArchUnit violation stores,
   `.github/workflows/`, `.claude/hooks/`, `scripts/githooks/`, this rules file.
3. **Work-order + report format** (seed: agent-protocol): delegated tasks state type,
   goal, read-first specs, file allowlist, done-tests, non-goals; work reports include
   files-vs-allowlist, before/after LOC of budgeted files, test output, and an
   invariant argument for G3. Standing rules: no drive-by improvements ("noticed, not
   done" list instead); **ratchet tampering is the one instant-revert offense**; agents
   never weaken or delete an assertion to make a suite pass.
4. **Review skills** (repo-local, `.claude/skills/`):
   - `review-markdown-integrity-data-loss-prevention` — seeded from the notebox skill,
     made platform-neutral (fail closed: when correctness is uncertain, do not write to
     disk, never partially transform user Markdown). Directly relevant: both apps write
     the same vault.
   - `review-state-consistency-coroutine-safety` — written fresh for Kotlin/Compose:
     StateFlow vs. snapshot-state drift, `viewModelScope` races, mutable state captured
     in composables, one-owner-per-state.
5. `AGENTS.md` gets a short "Change process" pointer to the rules file.

## Commit 5 — minimal observability spec

1. **`specs/observability/README.md`**: the Sentry event/tag naming conventions this app
   actually uses today (inventory the existing `captureMessage`/tag call sites first;
   document what exists, don't design aspirationally).
2. One binding rule, in that file and as a line in `AGENTS.md`: renaming a telemetry
   event or changing its tags/fingerprint updates the observability spec **in the same
   change**.
3. Add the now-existing `specs/observability/` folder to the doc map in `specs/README.md`
   (deferred from commit 2).

---

## Housekeeping note (separate from this plan's slices)

The synced skill `.cursor/skills/ubiquitous-language/` is stale
(`sync-shared-conventions.sh --check` fails on it). Fix by running the existing sync
script from notebox — **this stays under the existing sync mechanism** and must not be
mixed into any of the commits above, precisely because those introduce locally-canonical
material and this file remains notebox-owned. One trivial standalone commit, its message
naming the sync script.

## Lifecycle

Per `specs/plans/README.md`: each implementation commit deletes or shrinks its completed
slice from this plan. Durable outcomes land in their homes — `specs/rules/`,
`specs/README.md`, `AGENTS.md`, `specs/observability/`, the scripts and tests — and the
final implementation commit deletes this file.

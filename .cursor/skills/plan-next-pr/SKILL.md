---
name: plan-next-pr
description: >-
  Pick the next unit of work from specs/plans/, verify it against actual
  codebase state, ask at most 1-2 questions when the choice or scope is
  genuinely open, and produce a temporary per-PR work document with exactly one
  recommended LLM per step. Use when the user asks what to work on next, wants
  to start a PR from the plan stack, or says "plan the next PR".
---

<!-- AUTO-SYNCED from notebox — do not edit here. Canonical: notebox/.cursor/skills/plan-next-pr/SKILL.md -->
<!-- Re-run: notebox/scripts/sync-shared-conventions.sh -->


# Plan next PR (from specs/plans/)

Turn the plan stack into one executable, disposable work document for the current PR.
The output is **not** a new plan in `specs/plans/` — it is a temp doc that dies before the PR merges.

## Step 1 — Read the stack, in this order

1. [`specs/plans/README.md`](../../../specs/plans/README.md) — execution order (§4), gates, classifications (§7). This decides *which* plan is next; do not re-derive order from the plans themselves.
2. The candidate plan(s) for the next step, plus only the companion sections the README names.
3. The repo's change-safety taxonomy — every work-doc step gets a change-type.
   Use your repo's change-type taxonomy (e.g. eskerra-go's G1–G5 in `specs/rules/change-safety.md`).

## Step 2 — Verify against codebase state (never trust plan text)

Plans contain snapshots that drift. Before writing anything, verify the selected plan's assumptions with cheap commands and note discrepancies:

- **Gates actually met?** (e.g. "after podcasts pilot" → does the podcasts feature slice exist? "after change-safety PR 1" → does CI actually run the new check?) Check git log / file existence, not memory.
- **Referenced files still exist at the stated paths / sizes?** (`wc -l`, `ls`) Regenerate any inventory older than ~2 weeks (README lifecycle rule 6).
- **Branch state:** current branch, uncommitted work, whether an in-flight PR already covers this step.
- **Hold-lists and danger zones:** does the step touch a danger zone flagged in the repo's docs?
  The danger zones your AGENTS.md names — e.g. eskerra-go's `data/git` (all JGit mutations share one mutex; channel rules in the sync-hardening spec), credential storage, or the workspace scanner's symlink/`.git` skip rules.

**Abort rule (safety first):** if the codebase contradicts the plan — a gate is unmet, files moved, the phase is half-done, or the plan's premise no longer holds — **stop**. Do not bend the work doc to make it fit. Report the mismatch, propose the plan/README fix as the *actual* next PR, and let the user decide. A step back beats a confident wrong step.

## Step 3 — Ask at most 1–2 questions (only if needed)

Ask **nothing** when the README order dictates one obvious next step. Ask **one multiple-choice question** when 2–4 candidates are genuinely interchangeable (present them with one-line trade-offs). Ask a second question only for a scope fork that changes the work doc materially (e.g. "full phase or first PR of the series?"). Never more than two; never open-ended when choices can be enumerated.

## Step 4 — Write the work document

Path: `.claude/plans/pr-current.md`. **Never `git add` this file** — it must not appear in any commit or the PR diff. Format:

```markdown
# PR work doc — <branch> — <date>
Source: specs/plans/<plan>.md §<phase>   Delete-me-by: PR ready for review

Goal (1 sentence). Behavior change: yes/no. Change-type: <repo taxonomy>. Area/layer(s) touched: <...>.
Verified state: <the Step-2 checks that passed, one line each>
Stop conditions: <the specific mismatches that mean pause + report, from Step 2>

## Steps
1. <action> — **Model: <one name>** — check: <exact test/lint command>   <!-- append " — high: <one-clause reason>" only when the step needs high/xhigh; no suffix = medium -->
2. …
N-1. Plan hygiene: update/shrink/delete the source plan section + its
     specs/plans/README.md row to reflect what this PR completed.
N.   Delete this file. Verify with `git status` that it was never staged.
```

Rules for the doc:

- **Exactly one model per step — always named, no hedging.** Pick from the house roster by job type; if you can't pick one, the step is under-specified — split it. There is no "X, or Y if hard":
  - **Composer** — mechanical / high-volume / zero-judgment: verbatim moves, renames, transcription, recipe-driven test splits, trivial config edits.
  - **Cursor Grok** — fast, self-contained, low-risk single change with a cheap check: a small script, an isolated helper, a CI/YAML/build-config tweak, a one-file bugfix with an obvious shape.
  - **Sonnet** — the default implementer: pattern-following feature/impl work with tests, following an existing pattern in the repo.
  - **Terra** — larger or multi-file pattern-following work that must hold sustained context across several files but is *not* danger-zone (bigger non-invariant refactors; broad but low-risk edits).
  - **Opus** — judgment-heavy or stateful, non-danger-zone: editorial/plan-doc work, cross-cutting design decisions, async/concurrency reasoning that doesn't touch the repo's danger-zone invariants.
  - **Sol** — danger-zone / invariant-critical / adversarial: the repo's persistence/sync invariants, single-writer/mutex paths, cache-coherence code, concurrency races (closures/coroutines), and second-model review of a risky diff.
- **Effort: assume medium ("medium thinking") unless stated.** Write `high`/`xhigh` only when the step genuinely needs it (danger-zone invariant reasoning, race analysis, adversarial review) and append the one-clause reason after the model — e.g. `**Model: Sol** — high: sync/merge invariant reasoning`. No suffix means medium; a `high` with no reason is not allowed.
- Every step names its **check** — the exact command. A step without a check is two steps missing their seam.
  Your project's minimum + full gate (see AGENTS.md Commands) — e.g. eskerra-go: `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug` (minimum), `:app:testDebugUnitTest` when domain/data/logic changed, plus `./scripts/check-module-budgets.sh`.
- Respect the repo's standing house rules in every step.
  For eskerra-go: new `.kt` files ≤ 400 lines (baseline for exceptions, never casually bumped); ArchUnit layer rules hold; UI never reads files or calls Git; ViewModels depend on repositories/use-cases not `Context`; every feature slice ships at least one unit test.
- Steps must fit the house review rule: if the resulting PR can't be reviewed in ~30 minutes, plan a PR series (one work doc per PR, regenerated from the same source phase).
- The **plan-hygiene step is mandatory** (README lifecycle rules: delete on absorption, no trophies): completing a phase must shrink or delete its source plan text in the same PR, and update the README row if the classification changed.
- The **self-delete step is mandatory** and is the last thing done before requesting review.

## Step 5 — Hand off

Present the work doc contents to the user for a go/no-go. Do not start executing steps in the same turn unless the user already asked for execution. If mid-execution the codebase starts contradicting the doc (a check fails for plan-premise reasons, not typo reasons), apply the Step-2 abort rule: pause, report, re-plan — never improvise past a broken premise.

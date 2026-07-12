---
name: plan-next-pr
description: >-
  Pick the next unit of work from specs/plans/, verify it against actual
  codebase state, ask at most 1-2 questions when the choice or scope is
  genuinely open, and produce a temporary per-PR work document with exactly one
  recommended LLM per step. Use when the user asks what to work on next, wants
  to start a PR from the plan stack, or says "plan the next PR".
---

<!-- REPO-LOCAL skill (eskerra-go) — not part of the notebox sync set; sync-shared-conventions.sh leaves this directory alone. -->

# Plan next PR (from specs/plans/)

Turn the plan stack into one executable, disposable work document for the current PR.
The output is **not** a new plan in `specs/plans/` — it is a temp doc that dies before the PR merges.

## Step 1 — Read the stack, in this order

1. [`specs/plans/README.md`](../../../specs/plans/README.md) — execution order, gates, classifications. This decides *which* plan is next; do not re-derive order from the plans themselves.
2. The candidate plan(s) for the next step, plus only the companion docs the README names.
3. The house constraints every step must respect: [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../specs/adr/001-hybrid-layering-and-feature-slices.md) (placement rules), [`specs/architecture/sync-hardening-and-recovery.md`](../../../specs/architecture/sync-hardening-and-recovery.md) (git mutation rules), module budgets (`scripts/module-budget-baseline.json`).

## Step 2 — Verify against codebase state (never trust plan text)

Plans contain snapshots that drift. Before writing anything, verify the selected plan's assumptions with cheap commands and note discrepancies:

- **Gates actually met?** Check git log / file existence / feature-slice presence — not memory. External gates too (e.g. a phase gated on a Studio-side deliverable: verify it shipped, don't assume).
- **Referenced files still exist at the stated paths / sizes?** (`wc -l`, `ls`) Regenerate any inventory older than ~2 weeks.
- **Branch state:** current branch, uncommitted work, whether an in-flight PR already covers this step.
- **Caution areas:** does the step touch `data/git` (all JGit mutations share one mutex; channel rules in the sync-hardening spec), credential storage, or the workspace scanner's symlink/`.git` skip rules? Those steps get the heavy model tier and their tests in the same PR.

**Abort rule (safety first):** if the codebase contradicts the plan — a gate is unmet, files moved, the phase is half-done, or the plan's premise no longer holds — **stop**. Do not bend the work doc to make it fit. Report the mismatch, propose the plan/README fix as the *actual* next PR, and let the user decide. A step back beats a confident wrong step.

## Step 3 — Ask at most 1–2 questions (only if needed)

Ask **nothing** when the README order dictates one obvious next step. Ask **one multiple-choice question** when 2–4 candidates are genuinely interchangeable (present them with one-line trade-offs). Ask a second question only for a scope fork that changes the work doc materially (e.g. "full phase or first PR of the series?"). Never more than two; never open-ended when choices can be enumerated.

## Step 4 — Write the work document

Path: `.claude/plans/pr-current.md`. **Never `git add` this file** — it must not appear in any commit or the PR diff. Format:

```markdown
# PR work doc — <branch> — <date>
Source: specs/plans/<plan>.md §<phase>   Delete-me-by: PR ready for review

Goal (1 sentence). Behavior change: yes/no. Layer(s) touched: ui / feature / core / data.
Verified state: <the Step-2 checks that passed, one line each>
Stop conditions: <the specific mismatches that mean pause + report, from Step 2>

## Steps
1. <action> — **Model: <one name> (<effort>)** — check: <exact gradle/test command>
2. …
N-1. Plan hygiene: update/shrink/delete the source plan section + its
     specs/plans/README.md row to reflect what this PR completed; update
     specs/architecture/app-contract.md if a product boundary changed.
N.   Delete this file. Verify with `git status` that it was never staged.
```

Rules for the doc:

- **Exactly one model per step.** Use the house ladder: **Composer** for mechanical/high-volume/low-judgment work (verbatim moves, renames, transcription, budget-driven splits); **Sonnet or Terra** for pattern-following implementation with tests (pick one, by availability — write one name); **Sol or Opus** for judgment-heavy or caution-area steps (git mutations, credentials, scanner rules — again: one name). No "X, or Y if hard" hedging — if you can't choose, the step is under-specified: split it.
- **Effort:** default medium (or the model's default). Write `high`/`xhigh` only when the step genuinely needs it (sync/merge invariant reasoning, adversarial review) and add the one-clause reason. Absence of a reason means it shouldn't be high.
- Every step names its **check** — the exact command, e.g. `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug` (minimum) or `:app:testDebugUnitTest` when domain/data/logic changed, plus `./scripts/check-module-budgets.sh`. A step without a check is two steps missing their seam.
- Respect the house rules in every step: every feature slice ships at least one unit test; new `.kt` files ≤ 400 lines (baseline for exceptions, never casually bumped); UI never reads files or calls Git; ViewModels depend on repositories/use cases.
- Keep each PR reviewable in ~30 minutes; otherwise plan a PR series (one work doc per PR, regenerated from the same source phase).
- The **plan-hygiene step is mandatory** (delete on absorption, no trophies): completing a phase must shrink or delete its source plan text in the same PR, and update the README row if the classification changed.
- The **self-delete step is mandatory** and is the last thing done before requesting review.

## Step 5 — Hand off

Present the work doc contents to the user for a go/no-go. Do not start executing steps in the same turn unless the user already asked for execution. If mid-execution the codebase starts contradicting the doc (a check fails for plan-premise reasons, not typo reasons), apply the Step-2 abort rule: pause, report, re-plan — never improvise past a broken premise.

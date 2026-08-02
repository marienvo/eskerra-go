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

## Step 0 — Global urgency triage (before reading the stack order)

`specs/plans/README.md` stays the strategic source of ordering. This step exists only to
catch **evidenced operational interrupts** that are visible *without* having picked a
candidate yet; when the scan finds none, proceed to Step 1 and take the README's next item.
Priority classes (these are priority labels for the work doc, **not** change-types —
unrelated to any T1–T9 / G1–G5 change taxonomy):

- **T0 — interrupt now:** security vulnerability, credible data-loss/corruption risk, or failing CI on the default branch.
- **T1 — unblock now:** release blocker, or work blocking multiple active planned phases.
- **T2 — planned next:** the lowest unblocked item in `specs/plans/README.md`. The default.
- **T3 — opportunistic:** non-blocking cleanup, documentation, or maintenance. Never chosen over T2 by this skill; it exists to label work the user explicitly pulls forward.

Cheap **global** scan (minutes, not an audit) — signals that need no candidate:

- branch + uncommitted work (`git status`, `git log --oneline -5`);
- CI status on the repository's **default branch**, when checkable from this environment (e.g. `gh run list`);
- security or committed-secret findings, and credible persistence/corruption/data-loss risks already visible in repository state (a red default-branch suite, a tampered guardrail baseline, a secret in the tree);
- release blockers visible without opening plan files;
- **globally** documented time-sensitive dependencies (e.g. a dated cross-repo coordination deadline stated in the plans README or AGENTS.md).

Candidate-specific signals (gates, file drift, PR/worktree overlap, danger zones) are **not**
checked here — they belong to Step 2, after Step 1 has named the provisional candidate.

**Only T0 and T1 may override the README order**, and every override records its concrete
evidence in the work doc's `Why now`. When a signal (CI, PR state, external service) cannot
be inspected from the available environment, record it under `Triage not checked` — never
guess, and never present an unverified signal as evidence.

## Step 1 — Read the stack, in this order

1. [`specs/plans/README.md`](../../../specs/plans/README.md) — the execution-order section, gates, and plan classifications (section numbers differ per repo; use the README's own table of contents). This decides *which* plan is next; do not re-derive order from the plans themselves. **Its lowest unblocked item is the provisional T2 candidate** — Step 2 then verifies that candidate against the code.
2. The candidate plan(s) for the next step, plus only the companion sections the README names.
3. The repo's change-safety taxonomy — every work-doc step gets a change-type.
   Use your repo's change-type taxonomy (see AGENTS.md and `specs/rules/change-safety.md` when present).

## Step 2 — Verify the candidate against codebase state (never trust plan text)

Plans contain snapshots that drift. Before writing anything, verify the provisional candidate's assumptions with cheap commands and note discrepancies. These are the **candidate-specific** checks Step 0 deliberately skipped:

- **Gates actually met?** (e.g. "after podcasts pilot" → does the podcasts feature slice exist? "after change-safety PR 1" → does CI actually run the new check?) Check git log / file existence, not memory.
- **Referenced files still exist at the stated paths / sizes?** (`wc -l`, `ls`) Regenerate any inventory older than ~2 weeks (the README's snapshot-expiry lifecycle rule).
- **Overlapping work:** does an open PR, in-flight branch, or worktree already cover this step or touch the candidate's files? (Step 0 already established branch + uncommitted state; do not re-run it here.) When PR state is not inspectable from this environment, say so rather than assuming none. **Overlap is a plan-time warning only** — record it under `Warnings` in the work doc; never invent a BLOCKING GATE, never refuse to write the doc, never wait for a human to apply code.
- **Plan-vs-code contradictions:** does the candidate's premise still hold, or has the code moved past it?
- **Hold-lists and danger zones:** does the step touch a danger zone flagged in the repo's docs?
  The danger zones your AGENTS.md names — persistence/sync invariants, credential storage, scanner skip rules, and similar.

**Escalation:** a candidate-specific finding here may still justify a T0/T1 override — a
broken gate that blocks several phases, or a data-loss risk found in the candidate's own
surface. Record the concrete evidence in `Why now`, exactly as Step 0 requires; the priority
label is set once, from whichever step found the evidence.

**Re-plan rule (code wins, agents still build):** if the codebase contradicts the plan — a
gate is unmet, files moved, the phase is half-done, or the plan's premise no longer holds —
**do not bend facts** (code beats stale plan text). Report the mismatch, rewrite the work
doc and/or propose plan/README hygiene **in the same session**, and proceed. Never refuse
to build waiting for a human apply; never invent behavior to paper over a broken premise.

## Step 3 — Ask at most 1–2 questions (only if needed)

Ask **nothing** when the README order dictates one obvious next step. Ask **one multiple-choice question** when 2–4 candidates are genuinely interchangeable (present them with one-line trade-offs). Ask a second question only for a scope fork that changes the work doc materially (e.g. "full phase or first PR of the series?"). Never more than two; never open-ended when choices can be enumerated.

## Step 4 — Write the work document

Path: `.claude/plans/pr-current.md`. **Never `git add` this file** — it must not appear in any commit or the PR diff. Format:

```markdown
# PR work doc — <branch> — <date>
Source: specs/plans/<plan>.md §<phase>   Delete-me-by: PR ready for review

Goal (1 sentence). Behavior change: yes/no. Change-types: <one or more repo change-types; when multiple, name the commit boundary>. Area/layer(s) touched: <...>.
Priority: <T0|T1|T2|T3, from triage — Step 0 or a Step 2 escalation>. Why now: <one line; for T0/T1, the concrete override evidence>.
Triage checked: <what was actually inspected — the Step-0 global scan and the Step-2 candidate checks, one line>
Triage not checked: <signals not inspectable from this environment — include only when relevant>
Verified state: <the Step-2 checks that passed, one line each>
Warnings: <plan-time only — open-PR/worktree overlap, unmet soft gates, danger-zone touch; never blocking>
Stop conditions: <narrow — mismatches that mean rewrite steps in-session / do not invent behavior; never overlap or red-tier touch alone>

## Steps
1. <action> — **Model: <one name>** — check: <exact test/lint command>   <!-- append " — high: <one-clause reason>" only when the step needs high/xhigh; no suffix = medium -->
2. …
N-1. Plan hygiene: update/shrink/delete the source plan section + its
     specs/plans/README.md row to reflect what this PR completed; update
     any architecture/app-contract docs your AGENTS.md names if a product
     boundary changed.
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
  Your project's minimum + full gate (see AGENTS.md Commands).
- Respect the repo's standing house rules in every step.
  Module-size budgets, layer rules, and the seams AGENTS.md names; every touched module keeps or gains its tests.
- **Mechanical moves stay pure at the production-code level.** A step of the repo's mechanical-move change-type must never share a PR with a semantic, behavioral, or architectural production-code change. Required companion artifacts — documentation the source phase mandates (e.g. a slice/feature README), import-only test moves, generated-file re-syncs, and plan bookkeeping — may ride in the same PR, isolated in their own commits where that improves reviewability; they must never conceal a production behavior change. Unrelated cleanup gets its own PR.
- **One type per change; a PR may carry more than one change.** Every individual change still declares exactly **one** change-type — that rule lives in the repo's change-safety file and this skill does not relax it. A PR may contain several separately declared changes **only** where the source phase explicitly mandates companion artifacts (the bullet above). When it does, list every type in the header and name the commit boundary — e.g. `Change-types: G1 + G2 (one per commit)`. Multiple types are never a way to smuggle a mixed production-code change past review: if two types touch production behavior in one PR, it is mis-scoped and splits into two PRs.
- Steps must fit the house review rule: if the resulting PR can't be reviewed in ~30 minutes, plan a PR series (one work doc per PR, regenerated from the same source phase).
- The **plan-hygiene step is mandatory** (README lifecycle rules: delete on absorption, no trophies): completing a phase must shrink or delete its source plan text in the same PR, and update the README row if the classification changed.
- The **self-delete step is mandatory** and is the last thing done before requesting review.

## Step 5 — Hand off

Present the work doc contents to the user for a go/no-go. Do not start executing steps in the same turn unless the user already asked for execution. If mid-execution the codebase starts contradicting the doc (a check fails for plan-premise reasons, not typo reasons), apply the Step-2 re-plan rule: report, rewrite the remaining steps in-session, continue building — never invent behavior past a broken premise, and never freeze waiting for a human apply.

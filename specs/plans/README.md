# Plans README

Last reviewed: 2026-08-02 (stack rewritten from the 2026-07-05 audit + notebox 2026-07-19 sibling bar; `make-slices-real.md` added; parity plan re-verified; `workspace-setup.md` marked for disposal; `always-on-sync-and-spinner.md` added and parity P1b folded into it). Re-review whenever a plan completes or a new one lands.

## 1. Purpose of this folder

This folder holds **active or intentionally parked planning documents** — nothing else. A plan exists here because work is coming; once its decisions have landed in code, ADRs (`specs/adr/`), architecture docs (`specs/architecture/`), tests, or rules, the plan is deleted (or shrunk to the part still pending). Completed plans are not kept as trophies: git history remembers them. **The ideal end state of this folder is nearly empty.**

## 2. How to use these plans

1. Pick the next step from §4, not the most interesting plan.
2. Read only the plan you're executing plus the companions it names.
3. Convert **one phase** into a small PR or PR series (each reviewable in ~30 minutes). The `plan-next-pr` skill turns a phase into a disposable per-PR work doc.
4. After execution, move durable decisions out: `specs/adr/` for decisions, [`app-contract.md`](../architecture/app-contract.md) for product boundaries, tests/ArchUnit for invariants.
5. Then delete the executed part of the plan — or the whole plan.

**Warnings:**

- Plan text is never more authoritative than current code and tests. Snapshots drift; verify before executing, and when a plan contradicts the code, the code is the fact and the plan gets fixed.
- **A G1 production-code move never shares a PR with a semantic, behavioral, or architectural production-code change** — e.g. a ViewModel move and the non-inbox-editing rule flip must never meet. Required companion artifacts — documentation the phase mandates (a slice README), import-only test moves, generated-file re-syncs, plan bookkeeping — may accompany the move, isolated in their own commits where that improves reviewability; they must never conceal a production behavior change.
- **Never execute phases of both tracks in one PR.** One plan-phase per PR (or PR series); a phase's mandated companion artifacts count as part of that phase, not as a second phase. Unrelated cleanup gets its own small PR.
- Steps touching `data/git`, credentials, vault-write paths, or the workspace scanner rules are G3: tests in the same PR, red/yellow-tier rules apply (`specs/rules/change-safety.md`).

## 3. The two tracks

Deliberately orthogonal (notebox lesson: structure and product must not blur):

| | `make-slices-real.md` | `studio-feature-parity.md` |
|---|---|---|
| **Axis** | Quality / structure — where code lives, how it is guarded | Product parity — what the app does vs Studio |
| **Why it exists** | 2026-07-05 audit primary blocker: 11/12 ViewModels in `app/`; slices aspirational; 0 in-source docs; `core→data` leak; guardrail residuals | "Continue on your phone" breaks without non-inbox editing + sync moments; contract drift vs Studio |
| **Use it for** | VM moves with their slice README as mandated companion (Q0–Q1), README backfill for the slices no move covers (Q2), dependency inversion (Q3), app-shell thinning (Q4), CI zone gate / ArchUnit ratchet / CODEOWNERS (Q5), test splits (Q6) | P0 verified inventory, P1a non-inbox plain-Markdown editing, P2 contract adoption + small items (P1b now lives in `always-on-sync-and-spinner.md`) |
| **Must NOT be used for** | Any behavior or contract change; anything parity-shaped | Any file move or guardrail change; structure work hiding in feature PRs |
| **Status** | **Active now** (Q0 done; Q1 batch 1 is the next PR) | **Active** (P0 whenever a device session is available; P1a after P0; P1b absorbed by `always-on-sync-and-spinner.md`) |
| **Deletion condition** | Counters full (10/10 + 8/8), ArchUnit rules enforced, Q5 residuals merged, **and Q4 resolved** — done and its routing/shell decision absorbed into ADR-001 or `app-contract.md`, or explicitly parked/transferred to a named surviving doc; Q4 skipped does not count (its §Deletion) | P0, P1a, and P2 done (P1b via `always-on-sync-and-spinner.md`), `app-contract.md` updated, m4b gate re-pointed (its §5) |

| Other files | Class | Note |
|---|---|---|
| `always-on-sync-and-spinner.md` | **Active now** (product track; Phase A done) | Sync spinner in the badge slot (G2, landed), then writes-always-sync and boot/foreground-always-sync (both G3). **Absorbs parity P1b** — do not schedule P1b separately. Inherits P1b's hold against `make-slices-real.md` Q3. |
| `workspace-setup.md` | **Disposal pending** | Trophy log. One small PR: salvage the setup-rollback rule + setup modes/boundaries into `app-contract.md` (they are in no architecture doc today), then delete. |

Feature plans arriving later (e.g. the M4B audiobook player, whose cross-client contract lives in notebox) enter this table as **Active later** with their gate stated in the row.

## 4. Recommended execution order

The two tracks interleave freely **except** where a step below names a hold. Quality PRs are small and G1-heavy (agent-drivable); parity P0/P1a and the G3 sync phases need product judgment and a device — pull from whichever track matches the session, taking the lowest open step in that track.

1. ~~**Quality Q0 — pilot**~~ *(done — PR #33; retro written, Q1 gate cleared).*
2. **Parity P0 — verified inventory** (`studio-feature-parity.md`): doc-only correction of the matrix against the running app. *Unblocked; needs a device session; nothing parity-flavored may be scheduled off unverified rows until this lands.*
3. **`workspace-setup.md` disposal:** salvage → `app-contract.md`, delete the file. *Unblocked, trivial; an independent small doc PR of its own.*
4. **Quality Q1 — batch VM moves** *(unblocked; the default next PR — start with batch 1, `InboxViewModel` → `feature/inbox/`)*, each PR carrying its destination slice's README as mandated companion documentation. Per the Q0 retro: two VMs per PR when slice-coherent (one for `todayhub`/`sync`), and the `app/archunit_store/` rekey rides in the G1 commit. **Quality Q2** then backfills READMEs for the slices no move covers (`podcasts`, `menu`) as its own small doc PR — it is not executed inside a Q1 PR. Q6 test splits (G4) may interleave anytime, respecting move-first-then-split per file.
5. ~~**`always-on-sync-and-spinner.md` Phase A — sync spinner**~~ *(done on `always-on-sync-spinner`; gate green)*, then **Phases B and C** (G3, one PR each). These **replace parity P1b**, which they absorb.
6. **Parity P1a — non-inbox plain-Markdown editing** (after P0; its own disposable work doc and its own PR). P1a is the contract change: `app-contract.md` + AGENTS.md flip in the same PR as the domain rule. **Hold:** no Q-track PR touching the same slices/use-cases in the same review window; `always-on-sync-and-spinner.md` Phases B/C and Q3's G3 inversions touch the same sync use cases — whichever runs second waits.
7. **Quality Q3–Q5 — inversion, shell thinning, guardrail residuals** (Q3 after Q1 batches 1–4; Q4 after Q1; Q5 opportunistic, CODEOWNERS strictly after Q1).
8. **m4b audiobook Go phase** — gated on **P1a plus `always-on-sync-and-spinner.md` Phases B–C** (the former P1b) completing (gate lives in the notebox m4b plan §15, Phase 3, and here). This is a portfolio-sequencing / review-attention gate, not a technical dependency. Never interleaves with Q-track moves.
9. **Parity P2 — settings document (`vaultLayout`), theme read, attachments render, Today Hub depth** per P0 findings; coordinate with `notebox/specs/plans/desktop-settings-workspace.md`. **P3 long tail stays parked.**

## 5. Plan lifecycle rules

1. **Delete on absorption.** The PR that lands a phase's last artifact shrinks or deletes the plan text in the same change.
2. **No trophies.** "Status: complete" + checkmarks is the signal to delete, not to keep.
3. **Downgrade honestly.** Not intended for execution this quarter → mark parked here and stop maintaining its inventories.
4. **Snapshots expire.** Any file inventory or gap matrix is advisory after ~2 weeks; regenerate before acting.
5. **Product boundaries land in `app-contract.md`,** never only in a plan.
6. **Live counters only while coordinating** a running multi-PR effort; they go when coordination ends.
7. This README follows its own rules: if the folder shrinks to one plan, fold this file into a paragraph in that plan.

## 6. Recommended immediate next action

- **Next PR:** quality **Q1 batch 1** — `InboxViewModel` + test → `feature/inbox/` (G1, including the `app/archunit_store/` rekey) + `feature/inbox/README.md` (G2). Checks: `./scripts/check-module-budgets.sh` && `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`. Q0 is done (PR #33) and its retro cleared the gate.
- **Also unblocked, next device session:** parity **P0** (doc-only matrix verification).
- **Independent small doc PRs (each gets its own PR):** `workspace-setup.md` disposal; the AGENTS.md broken-link fix (`android-vault-notes-rebuild-plan.md` — see `make-slices-real.md` §Follow-ups; it may join another PR only when that PR already edits the same documentation surface).
- **Do not touch yet:** parity P1a/P1b (blocked on P0); Q3's G3 inversions (after Q1 batches 1–4, never alongside P1b); CODEOWNERS (after Q1); m4b Go work (blocked on P1a+P1b); scheduled background sync (Tier D in `app-contract.md` — a product decision, not backlog); proactive splitting of the pinned podcasts files (split-before-grow only, on touch).

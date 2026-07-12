# Plans README

Last reviewed: 2026-07-12. Re-review whenever a plan completes or a new one lands.

## 1. Purpose of this folder

This folder holds **active or intentionally parked planning documents** — nothing else. A plan exists here because work is coming; once its decisions have landed in code, ADRs (`specs/adr/`), architecture docs (`specs/architecture/`), tests, or rules, the plan is deleted (or shrunk to the part still pending). Completed plans are not kept as trophies: git history remembers them. **The ideal end state of this folder is nearly empty.**

## 2. How to use these plans

1. Pick the next step from §4, not the most interesting plan.
2. Read only the plan you're executing plus the companions it names.
3. Convert **one phase** into a small PR or PR series (each reviewable in ~30 minutes). The `plan-next-pr` skill turns a phase into a disposable per-PR work doc.
4. After execution, move durable decisions out: `specs/adr/` for decisions, [`specs/architecture/app-contract.md`](../architecture/app-contract.md) for product boundaries, tests for invariants.
5. Then delete the executed part of the plan — or the whole plan.

**Warnings:**

- Plan text is never more authoritative than current code and tests. Plans contain snapshots that drift; verify before executing, and when a plan contradicts the code, the code is the fact and the plan gets fixed.
- Steps touching `data/git`, credentials, or the workspace scanner rules carry their tests in the same PR (see the sync-hardening spec); never batch them with unrelated work.
- One phase per PR series; never two plans' phases in one PR.

## 3. Current plans

| Plan | Class | Note |
|---|---|---|
| `studio-feature-parity.md` | **Active now** | The stack's driver. Three-tier parity model (contract / product / deliberate divergence). Next step: **P0 verified inventory** (no code — correct the plan's own gap matrix against the running app). P1 (non-inbox editing + foreground-resume sync) is the headline product gap and gates the audiobook player's Go phase. |
| `workspace-setup.md` | **Candidate for deletion** | A "what was built" log for the (long-shipped) setup flow — history, not a plan. Anything durable in it belongs in `app-contract.md` or an architecture doc; verify nothing is missing there, then delete. |

Feature plans arriving later (e.g. the M4B audiobook player, whose cross-client contract lives in the notebox repo) enter this table as **Active later** with their gate stated in the row.

## 4. Recommended execution order

1. **Parity P0 — verified inventory.** No code; corrects `studio-feature-parity.md` §3 against the actual app. Nothing may be scheduled off unverified matrix rows.
2. **`workspace-setup.md` disposal.** One small PR: salvage anything `app-contract.md` lacks, delete the file.
3. **Parity P1 — non-inbox editing + foreground-resume sync.** The deliberate contract change (`app-contract.md` + AGENTS.md updated in the same PR as the domain rule flips). Feature-slice tests mandatory; module budgets hold.
4. **Parity P2 — contract adoption + small items** (settings document / `vaultLayout`, theme-preference read, attachments render, Today Hub depth per P0 findings). Independent small PRs.
5. **Audiobook player Go phase** — unlocked by step 3; its own plan governs it.
6. **Parity P3 long tail** — explicitly allowed to stay parked.

## 5. Plan lifecycle rules

1. **Delete on absorption.** The PR that lands a phase's last artifact shrinks or deletes the plan text in the same change.
2. **No trophies.** "Status: complete" + checkmarks is the signal to delete, not to keep.
3. **Downgrade honestly.** Not intended for execution this quarter → mark parked here and stop maintaining its inventories.
4. **Snapshots expire.** Any file inventory or gap matrix is advisory after ~2 weeks; regenerate before acting.
5. **Product boundaries land in `app-contract.md`,** never only in a plan — the contract doc is what future work reads.
6. This README follows its own rules: if the folder shrinks to one plan, fold this file into a paragraph in that plan.

## 6. Recommended immediate next action

- **Next PR:** parity **P0** — verify the gap matrix against the running app; doc-only.
- **Delete first:** `workspace-setup.md` after its salvage check.
- **Do not touch yet:** audiobook Go work (gated on parity P1) and any scheduled-background-sync ambitions (deliberate divergence per `app-contract.md` — a product decision, not a backlog item).

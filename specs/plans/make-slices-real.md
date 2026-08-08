# Make Slices Real — quality/structure plan

Date: 2026-08-02
Status: **active — the quality track's flagship.** Orthogonal to `studio-feature-parity.md` (product track): this plan changes **where code lives and how it is guarded**, never what the app does. A phase here must never share a PR — or a review window on the same files — with a parity contract change.

Source: the 2026-07-05 audit (`.me/monthly-reports/report-2026-07-05.md`) — primary blocker "feature slices are not self-contained; 10/11 ViewModels live in `app/`" — re-verified 2026-08-02 (now **11/12**: `BinariesViewModel` landed in `app/` post-audit; only `podcasts` is self-contained). Post-audit process wins (budget ratchet, ArchUnit + frozen store, G1–G5 rules, observability spec) are **done** and not re-planned here; only their residuals appear (Phase Q5).

Companion docs: `specs/adr/001-hybrid-layering-and-feature-slices.md` (the claim this plan makes true), `specs/rules/change-safety.md` (G-types used below), `specs/team-scalability/README.md` (budgets).

## Live counter (delete with the plan)

ViewModels moved into their slice: **10 / 10 — done** (excluded by design: `AppGateViewModel` — genuinely app-level). Slice READMEs written: **6 / 8** — the eight beyond Q0's `search` pilot: six delivered as Q1 move companions (`inbox`, `editor`, `note`, `todayhub`, `setup`, `sync`) and two backfilled by Q2 (`podcasts`, `menu`).

## Design rules (binding for every phase)

1. **Moves are verbatim.** G1 move commits contain `git mv` + import/package rewrites only; zero behavior edits, zero test-body edits beyond imports. A tempting cleanup goes in the "noticed, not done" list.
2. **G1 moves stay pure at the production-code level.** A G1 move never shares a PR with a semantic, behavioral, or architectural production-code change (house rule; also the notebox lesson). Required companion artifacts — the slice README a phase mandates, import-only test moves, plan bookkeeping — may share the PR, isolated in their own commits (the Q0 pattern); they must never conceal a production behavior change. Unrelated cleanup gets its own PR.
3. Budgets hold: moved files inherit the old path's merge-base size (checker rule); new READMEs are docs, not code.
4. Each PR reviewable in ~30 minutes; batch phases become PR series.
5. Snapshots in this plan (counts, file lists) expire in ~2 weeks; regenerate before executing (`ls app/*ViewModel*`, `grep -rn "import com.eskerra.go.data" app/src/main/java/com/eskerra/go/core/`).

## Phases

### Q1 — VM moves *(moves complete; one guardrail item left)*

- **Done 2026-08-08:** all 10 movable ViewModels now live in their slice (`ls app/src/main/java/com/eskerra/go/app/*ViewModel*` = `AppGateViewModel.kt` only, which stays by design). Six batches, each a G1 move plus its slice README as mandated companion, each with the additive `app/archunit_store/` update in the move commit.
- **Remaining — the guardrail (G5, maintainer, its own PR):** add an ArchUnit rule "no class named `*ViewModel` resides in `..app..` except `AppGateViewModel`" so the hub cannot re-form. It is not a companion of any move, so it never rode along with one.
- **On merge of that rule:** revisit the dated ADR-001 Consequences note (it currently says the rule is pending rather than enforced), then **delete this phase**.
- **Checks:** `./scripts/check-module-budgets.sh` && `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`.

### Q2 — Slice-README backfill (the slices no move covers)

- **Why:** audit's −0.30 penalty: zero in-source docs; slices need code archaeology to onboard. Q0 and Q1 cover every slice that receives a ViewModel; this phase closes the rest so the counter can actually reach 8/8.
- **Gate:** none for the two slices below. For any slice that *does* have a move pending, the README belongs to that move unit (Q0/Q1), not here — writing it earlier would document the wrong layout.
- **Scope:** `feature/<name>/README.md` for the slices no move unit delivers:
  - **`podcasts`** — already self-contained; it is the template slice, so its README doubles as the reference example. Write it any time.
  - **`menu`** — single-screen slice with no ViewModel; nothing will ever move into it.
  - **plus any README the live counter still shows missing** once the Q1 series ends (e.g. a batch that shipped its move but skipped its doc — that is a Q2 backfill, and a signal the Q1 companion rule was not followed).
- Same 5-section template as the Q0 pilot — copy `app/src/main/java/com/eskerra/go/feature/search/README.md` (~45 lines, hard cap 80). Do **not** duplicate global specs; link `sync-hardening-and-recovery.md` etc. from the sync README instead of restating it.
- **G-type:** G2 (docs). Delivery: its own small documentation PR (one or both slices), independent of the Q1 series.
- **Acceptance:** every `feature/*` slice has a README; counter 8/8 (the eight beyond Q0's `search` pilot).
- **Shrink rule:** phase deletes when the counter fills.

### Q3 — Invert `core → data`; kill `AppGateResolver → app`

- **Why:** audit finding #1/#4: 30 concrete `data` imports across 9 `core/usecase` files (grew from 29 — `SyncBinaries` added post-audit), plus the one reverse leak `data/workspace/AppGateResolver.kt:3` importing `app.AppGateState`. Layering is convention-enforced, not compile-enforced.
- **Gate:** cleared — the Q1 moves are done, so no move PR competes for these files. The old "never alongside parity P1b" hold is released: the always-on-sync work delivered P1b and is closed, so no parity phase competes for these sync use-case files.
- **Scope (PR series, smallest first):**
  1. `AppGateResolver`: move `AppGateState` (or an interface for it) into `core`/`data`-appropriate home; delete the `app.` import. Small, self-contained.
  2. Lower-risk (non-G3) dependency inversions — behavior-preserving interface extraction on the use cases outside the G3 trio (`SyncBinaries`, `LoadGitStatusSummary`, `BuildSafeSyncDiagnostic`, `BuildSyncPreflight`, `LoadSyncStatus`, `RefreshRemoteSyncStatus`): introduce `core` interfaces for the `data` concretions they name (`WorkspacePaths`, `CredentialStore`, error mappers…), wire in `app/`.
  3. The G3 trio (`ManualSyncNow`, `SyncPodcastChange`, `ReconcileWorkspaceSyncBranch` — sync orchestration, G3 per `change-safety.md`; the first two are also named yellow-tier files): same inversion, **G3** — agent applies with invariant argument (single mutex, fail-closed recovery, scoped staging) and sync/recovery/mark-as-played suites in the same PR.
- **G-type:** G2 for steps 1–2 (interface extraction, behavior-preserving), **G3** for step 3.
- **Acceptance:** `grep -rn "import com.eskerra.go.data" app/src/main/java/com/eskerra/go/core/` = 0; then add the ArchUnit rule "core may not depend on data" as an *enforced* (non-frozen) rule (G5).
- **Checks:** full gate every PR; step 3 additionally names the sync suites in its work order.
- **Shrink rule:** phase deletes when the grep hits zero and the ArchUnit rule merges.

### Q4 — Thin the `app/` shell; localize routing

- **Why:** audit risk #2: routing changes ripple through `App.kt` (344) / `AppNavGraph.kt` (370) / `AppRoute.kt`; `app/` is 48 flat files that are individually clear, collectively unnavigable.
- **Gate:** cleared — the Q1 moves are done (they shrank `app/` for free and clarified what remains).
- **Scope, deliberately modest — this is a tidy-up, not a framework:**
  1. Sub-group `app/` into packages (`app/shell/`, `app/nav/`, `app/gate/`, root wiring stays top-level). Pure G1 `git mv` series.
  2. **Design note first, then decide** on per-slice route registration: write ≤1 page comparing (a) status quo after sub-grouping, (b) a `NavGraphBuilder.<slice>Routes()` extension per slice (routes declared in `feature/<name>/`, registered by one thin `AppNavGraph`). Adopt (b) only if the note shows it reduces the files touched by "add a screen" from ≥3 to ≤2 without indirection cost. No routing DSL, no reflection, no third option.
- **G-type:** G1 (sub-grouping), G2 (route extensions if adopted), the design note is a plan artifact.
- **Acceptance:** `app/` root ≤ ~15 files; "add a nav route" touches ≤2 files if (b) adopted; suite green.
- **Shrink rule:** design note's decision moves into ADR-001's amendment (see Follow-ups) or a one-liner in `app-contract.md`; phase deletes.

### Q5 — Guardrail residuals (post-audit process wins: finish, don't rebuild)

Independent of Q1–Q4; schedule opportunistically. All G5, maintainer-reviewed.

1. **Zone gate in CI:** port the *shape* of notebox's `check-change-safety-zones` — a script + `android-ci.yml` step failing a PR that touches red-tier globs (`data/git/**`, vault-write paths, FTS reconcile, ratchet files, workflows) without a `G3`/`G5` declaration line in the PR body. Mechanism may be agent-built; the glob list is policy (human). This replaces "another prose rewrite" of change-safety.md — the file is fine; it just isn't machine-checked.
2. **ArchUnit ratchet honesty:** the frozen violation store (`app/archunit_store/`) currently has nothing forcing shrink-only. Add a check mirroring the budget baseline rule: the store may lose lines, never gain (CI diff check). **Sequencing (learned in batches 1–2):** every remaining Q1 move *grows* the store, because moving a ViewModel from `app/` into `feature/` brings pre-existing `java.io.File` usage into the rule's scope. So this item lands **after** Q1 completes, or it ships with an explicit move exemption — otherwise it blocks its own track. Burn-down of the `java.io.File`-in-feature violations is opportunistic only, and only inside a PR already making semantic changes to those files — **never** inside a G1 move PR (burn-down changes signatures) and never as its own mass edit.
3. **CODEOWNERS:** unblocked — the Q1 moves are done, so the paths ownership keys to are now stable (the audit's own sequencing lesson: ownership needs paths). Keyed to `feature/*`, `data/git/**`, `core/**`, `scripts/**`, `.github/**`. Solo-era value is routing review attention + making red-tier ownership explicit, not enforcement.
- **Acceptance:** each item = its own small PR with the guardrail's own test (`check-*.test.sh` pattern).
- **Shrink rule:** delete per item on merge.

### Q6 — Split the oversized test megamodules (G4)

- **Why:** audit −0.10 penalty; parallel edits are awkward in the largest test files.
- **Threshold (explicit):** a file is in scope while it is **strictly over 450 LOC** (`> 450`); at or under 450 it already satisfies acceptance and is not scheduled.
- **Scope (counts regenerated 2026-08-02; re-run `wc -l` before executing — snapshots expire):**
  - `data/workspace/RemoteSyncSettingsRepositoryTest.kt` — 571. No Q1 move pending (not a ViewModel test): splittable now.
  - `core/usecase/ManualSyncNowTest.kt` — 552. Same: splittable now.
  - `feature/podcasts/PodcastsViewModelTest.kt` — 480. Already slice-local (no Q1 move pending): splittable now.
  - `feature/inbox/InboxViewModelTest.kt` — 457. Move landed (Q1 batch 1) without a split, per **move first, then split**: splittable now.
  - `data/workspace/WorkspaceSetupRepositoryTest.kt` — 450: already satisfies acceptance; **not scheduled** (do not invent splits).
- **Gate:** none — G4 is the ideal idle-agent task. Coordination rule: a test file being split and being moved never overlap in one review window; per-file, **move first, then split** (Q1 wins ties).
- **How:** split by scenario group (e.g. `ManualSyncNowTest` → commit/stage, integrate/merge, recovery, push-retry); zero production diff, zero assertion changes.
- **Acceptance:** no scoped file over 450 LOC; baseline entries lowered via `./scripts/update-module-budget-baseline.sh`.
- **Shrink rule:** delete per file.

### Hotspot rule (standing, not a phase)

`PodcastsScreen.kt` (538) and `PodcastsViewModel.kt` (482) are pinned: **split before grow.** Any feature work that must enlarge them starts with a G1 extraction PR. Not scheduled proactively — the budget checker already blocks growth; proactive splitting of untouched files is churn.

## Follow-ups this plan must schedule (docs honesty)

- ~~**ADR-001 amendment** after Q1 batch 3~~ *(done — landed with batch 3; the dated Consequences note says the ArchUnit rule is still pending rather than claiming enforcement, and the note must be revisited when the Q1 guardrail merges).*
- **AGENTS.md broken link** (its own tiny doc PR; it may join another PR only when that PR already edits AGENTS.md or the same documentation surface): `specs/plans/android-vault-notes-rebuild-plan.md` doesn't exist. Recover the FTS index-schema/reconcile/ranker content from git history into `specs/architecture/vault-search.md` (or point at the actual current source) and fix the link.
- **`SyncSettingsViewModel` and `BinariesViewModel` have no unit tests** (found while moving them in batches 5 and 6; both arrived in `feature/sync/` untested, and a G1 move PR is the wrong place to add one). Not a Q6 item — Q6 is test *splits* over 450 LOC. Write them with the next semantic change to either file, or as one small G4 PR: for `SyncSettingsViewModel`, save / test-connection / clear plus the rule that a successful save clears `replacementToken`; for `BinariesViewModel`, the list/refresh and sync-failure paths.
- **AGENTS.md / change-safety.md**: when Q5.1 lands, change-safety.md's tier section gains one line: "machine-checked in CI".

## Parked (explicitly, with reasons)

- **Explicit composition/DI object** — audit's optional +1; real risk of becoming a framework project; `remember{}` wiring is tolerable at current size. Revisit if Q4 leaves wiring illegible.
- **Gradle multi-module split** — audit "what breaks at 2×" item; premature at 28k LOC and solo cadence.
- **Instrumented/UI smoke auto-run in CI** — first *define* "good enough" (notebox bar: N consecutive green runs, each flow proven red under a deliberate break) as a design note; no CI wiring before that note exists.
- **Proactive burn-down of the ArchUnit `java.io.File` store as its own project** — opportunistic only (Q5.2).

## Deletion condition

Delete this plan when: counter reads 10/10 + 8/8, the Q3 grep is zero, both ArchUnit rules (VM placement, core→data) are enforced, Q5's three residuals merged, **and Q4 is resolved** — resolved meaning one of exactly two outcomes, written down:

- **Q4 done:** the sub-grouping landed and the routing design note's decision is absorbed into the ADR-001 amendment or `app-contract.md`; or
- **Q4 parked or transferred:** the phase is explicitly parked, or moved into a named surviving document (`specs/team-scalability/README.md` backlog, or an architecture doc), with the reason recorded there.

**Q4 silently skipped does not satisfy deletion** — the routing/shell decision must live somewhere that outlives this file.

Survivors: ADR-001 amendment, slice READMEs, ArchUnit rules, CODEOWNERS, the CI zone gate, and Q4's decision in its named home. Q6 leftovers become backlog lines in `specs/team-scalability/README.md`, not a reason to keep the plan.

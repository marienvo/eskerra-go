# Studio Feature Parity — Eskerra Go plan

Date: 2026-07-12; repo-state facts re-verified 2026-08-02 (§2 corrected — product-level rows still need P0's on-device pass).
Status: **planning only.** Sequenced **before** the M4B audiobook player: `notebox/specs/plans/m4b-audiobook-player.md` Phase 3 (Go player implementation) is gated on this plan's P1 completing. The audiobook contract/fixture work (its Phases 0–1) is independent and may run in parallel.
Companion docs: [`specs/architecture/app-contract.md`](../architecture/app-contract.md) (current product boundaries — this plan proposes changes to it, phase by phase), notebox plan stack (`notebox/specs/plans/README.md`). This is the **product track**; structure work lives in [`make-slices-real.md`](make-slices-real.md) and never shares a PR with a phase here.

## 1. Purpose and parity philosophy

Eskerra Go is a **companion client over the same vault contract**, not a Studio port. "Feature parity" therefore has three tiers, and every gap in §3 is assigned one:

- **Tier C — contract parity (mandatory):** anything both clients persist or sync must have identical semantics: vault layout, `.eskerra/` documents, playlist protocol, binaries sync, git sync safety rules, (future) audiobook progress. A contract-parity gap is a bug.
- **Tier P — product parity (planned):** capabilities Go should have because the product story ("continue on your phone") breaks without them. These are this plan's phases.
- **Tier D — deliberate divergence (documented, not planned):** things Go intentionally does not do. Divergence is fine when written down; drift is what this plan eliminates.

## 2. Current state (repo facts re-verified 2026-08-02; on-device behavior still pending P0)

Go today (from `app-contract.md` + the **actual** feature slices `feature/{editor,inbox,menu,note,podcasts,search,setup,sync,todayhub}` — there is **no** `feature/settings/` slice; earlier revisions of this plan and the 2026-07-05 audit claimed one. Settings UI lives in `feature/sync/` (`SyncSettingsScreen`, `SyncSettingsSections`, `VaultSettings*` state) with its ViewModels in `app/`): git-first single-workspace setup; inbox notes create/edit, **all other notes read-only**; markdown reader with wiki links; FTS5 full-text search; manual vault sync + podcast-specific sync channels (one JGit mutex, conflict sidecars, fast-forward-only for mark-played); podcasts (catalog, Media3 playback with media session/notification, R2 playlist handoff mirroring `playlist.ts`, RSS refresh, mark-as-played); a Today Hub surface (`todayhub` slice; depth unverified — P0); R2 binaries (m4b) sync with downloaded-binaries tile (landed 2026-07, PR #27).

Studio capabilities Go lacks or may lack (from the notebox audit 2026-07-11 + specs): full note editing (CodeMirror tables, frontmatter editor, date tokens, wiki-link creation), Today Hub authoring (rows, calendar agenda), automated git sync moments (startup/close/periodic — desktop has them; Go is manual-only), theme preference sync (R2 `theme-preference.json`), attachments/image handling, calendar/ICS pipeline, reminders (Linux daemon on desktop), settings document adoption (`appSettings` in `settings-shared.json`, incl. `vaultLayout` — the notebox settings plan has an open question waiting on Go), quick-open/vault-tree navigation.

**Do not trust this list blindly** — P0 verifies it against the running app before anything is scheduled.

## 3. Parity matrix

**Verified** = confirmed against code/specs 2026-08-02. **P0** = needs the on-device pass before anything is scheduled from the row.

| Domain | Studio | Go today | Tier | Verified? | Priority |
|---|---|---|---|---|---|
| Vault/`.eskerra/` contract, playlist protocol, binaries sync, git safety rules | full | full | **C — in parity** | **Verified** (code: `PlaylistMerge.kt` mirrors `playlist.ts`; `SyncBinaries` + manifest; sync-hardening spec matches channels) | maintain (fixtures where they exist) |
| Podcast semantics (mark-played, RSS, playlist merge) | full | full | **C — in parity** | **Verified** (code + suites) | maintain |
| Non-inbox note **editing** | full editor | read-only | **P** | **Verified** (domain rule in core; AGENTS/app-contract state it) | **P1 — the headline gap**; scope: plain-markdown editing with the same save safety (sidecars, mutex), *not* CodeMirror table/frontmatter UI |
| Today Hub | authoring canvas | surface exists, depth unverified | **P** | **P0** — depth/authoring status unknown | P2 (after P0; likely: row viewing → row editing) |
| Sync moments | startup/close/auto + polling | manual + read-only foreground `fetch` for the shell indicator | **P (partial)** | **Verified** (sync-hardening spec: foreground-resume *status* refresh exists; a sync *trigger/prompt* does not) | P1: foreground-resume sync prompt/trigger; scheduled background sync stays **Tier D** (app-contract: no WorkManager) |
| Theme preference sync | R2 + settings mirror | not found in code | **P** | **P0** — confirm absence on device | P2 (small; read side first) |
| Settings document adoption (`appSettings`, `vaultLayout`) | shipping (settings plan; `VaultLayoutConfig` + Go compliance warnings landed notebox-side 2026-06-24) | not adopted | **C once Studio ships it** | **Verified** not adopted | P2 — unblocks the notebox settings plan's open question; unknown-key preservation is the guardrail |
| Attachments/images in reader | full | unverified | **P** | **P0** | P2 (render-only; paste/upload later) |
| Audiobook player | planned (m4b plan) | planned | **C for contract, P for UI** | **Verified** planned-only both sides | after this plan's P1 (m4b plan Phase 3) |
| Calendar/ICS pipeline | full | none authored on Go; git sync delivers generated content | **P (low)** | **P0** — confirm rendering of generated Today content | P3 — consumer side only |
| Reminders | Linux daemon | none | **D for the daemon**; Android-native reminders would be a *new feature plan*, not parity | Verified | not scheduled |
| Multi-workspace, SSH remotes, scheduled background sync, editor megafeatures (tables UI, frontmatter UI, date tokens) | — | — | **D** | Verified (listed in `app-contract.md` §out of scope) | documented in app-contract; revisit only on explicit product decision |

## 4. Phases

- **P0 — Verified inventory (small, do first; needs a device session).** The 2026-08-02 repo-side pass is done (see §3 Verified column); what remains is the **on-device** check of the four rows marked P0 (Today Hub depth, theme preference, attachments render, calendar-content rendering). Correct this file; move anything mis-tiered. Update `app-contract.md` only where it is already wrong today. Acceptance: every §3 row marked Verified; no code changes.
- **P1 — Editing + sync-moment foundation.** (a) Non-inbox note editing behind the existing sidecar/mutex safety (this deletes the "inbox editable, others read-only" domain rule — a deliberate contract change, updated in `app-contract.md` + AGENTS.md in the same PR); (b) foreground-resume sync trigger. Feature-slice tests per house rule; module budgets hold. **This is the gate for m4b Phase 3** — not because audiobooks need editing, but because P1 also generalizes Go's willingness to change core contract rules deliberately, and because the audiobook Go work should not compete with the headline parity gap for the same review attention.
- **P2 — Contract adoption + small parity items.** `appSettings`/`vaultLayout` adoption (coordinate with notebox settings plan §7), theme-preference read, attachments render, Today Hub depth per P0 findings. Independent small PRs; any item can be dropped to P3 without blocking others.
- **P3 — Long tail.** Calendar-content rendering; anything P0 demoted. Explicitly allowed to stay parked.
- **P4 — Absorb.** Durable outcomes land in `app-contract.md` (updated capabilities + divergence list); this plan deletes.

Ordering vs. the notebox stack: this plan touches **no** notebox code and does not interact with domain-homes/cutover sequencing. Cross-repo coordination points are exactly two: settings-document adoption (P2 ↔ notebox settings plan) and the m4b Phase 3 gate.

## 5. Deletion condition

Delete when P0–P2 are done, `app-contract.md` reflects the new capability + divergence lists, and the m4b plan's Phase 3 gate references app-contract instead of this file. P3 leftovers become backlog notes, not a reason to keep the plan.

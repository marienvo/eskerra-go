# Phase 7 execution plan — Vault search (spec §12)

Companion to [`android-vault-notes-rebuild-plan.md`](./android-vault-notes-rebuild-plan.md)
(see its **Phase 7** entry and §4 *core/search* building blocks) and to **§12** of
[`android-vault-notes-rebuild-spec.md`](./android-vault-notes-rebuild-spec.md). This file expands
Phase 7 into an ordered, test-first commit sequence for the **eskerra-go** Kotlin/Compose app.

Phases 0–6 are complete and committed on `markdown-vault`. Phase 7 is the last feature phase before
the §10 dark-mode audit / acceptance pass (Phase 8).

---

## 0. Scope & decisions

| Topic | Decision | Source |
| ----- | -------- | ------ |
| Index engine | **Android's bundled SQLite FTS5** via raw `SQLiteOpenHelper`. No Room, no Rust/RN native module. Explicit schema keeps reconcile/ranking queries readable. | plan §0, Phase 7; `AGENTS.md` FTS5 rule |
| Reconcile cadence | **Foreground only.** Warm after a vault session exists and on search-screen open; AppState-active + 5-minute interval reconcile. Background WorkManager reconcile **deferred** (respects "no background sync" — `AGENTS.md`). | plan risk #4; spec §12.1 |
| Eligibility | Reuse **`core/vault/VaultVisibility`** (Phase 1) for `isEligibleVaultMarkdownPath`; do **not** re-derive the rules. | plan §4 |
| Paths | Plain vault-relative `NoteId`/`NotePath` (no SAF). The spec's `uri` column stores the vault-relative path; `vaultInstanceId` derives from the canonical workspace base, not a SAF tree URI. | plan §0 |
| Placement | Pure logic → `core/search`; index + reconcile → `data/search` (interface in `core/repository`); screen + state → `feature/search`; ViewModel + route in `app/`. | ADR-001 |

**Non-goals for Phase 7:** background WorkManager reconcile; editing from search results; cross-vault
search; ranking config UI.

---

## 1. Module placement (ADR-001) & file budget

Keep every new `.kt` **≤400 lines** (`scripts/check-module-budgets.sh`). Pre-split the ranker so no
single file carries query-building + ranking + snippeting.

| New code | Package | Notes |
| -------- | ------- | ----- |
| `Fts5Query` builder | `core/search/Fts5Query.kt` | pure, tested |
| Ranking tiers + `compareVaultSearchNotes` | `core/search/SearchRanker.kt` | pure, tested |
| Snippet + `vaultSearchHighlightSegments` | `core/search/VaultSearchHighlight.kt` | pure, tested |
| Search result/query models | `core/search/VaultSearchModels.kt` | pure data |
| Repository contract | `core/repository/VaultSearchRepository.kt` | interface only |
| FTS5 schema + open helper | `data/search/VaultSearchDatabase.kt` | `SQLiteOpenHelper`, schema-version table |
| Index fill + incremental reconcile | `data/search/VaultSearchIndexer.kt` | titles→bodies phases; `(size,lastModified)` diff |
| Repository impl (query + maintenance) | `data/search/SqliteVaultSearchRepository.kt` | wires DB + indexer + ranker |
| Use cases | `core/usecase/SearchVault.kt`, `core/usecase/MaintainVaultSearchIndex.kt` | orchestration |
| Screen + rows | `feature/search/SearchScreen.kt`, `feature/search/SearchResultRow.kt` | stateless |
| UI state | `feature/search/SearchUiState.kt` | sealed |
| ViewModel + factory | `app/SearchViewModel.kt` | debounce/cancel/stale-guard |
| Route wiring | `app/SearchRoute.kt` (+ `AppRoute`, `AppShell`, `App`, `AppRoot`, `MainActivity`) | mirrors Today Hub wiring |

---

## 2. Test vectors

Seed `core/search` tests from the `@eskerra/core` golden cases in **notebox** — copy, do not
re-derive:

- `vaultSearchHighlight.test.ts` → `VaultSearchHighlightTest.kt` (segment offsets, multi-token, case-fold).
- Fts5 query building cases (whitespace tokenize, strip `"()\\`, drop `and|or|not|near`, implicit AND).
- Ranker tier cases (exact 40k / prefix 25k / fuzzy 12k / body BM25×0.02; `bestField` title>path>body;
  `matchCount` ≥ 1; `compareVaultSearchNotes` order).

`data/search` gets JVM tests against an in-memory / temp-file SQLite (Robolectric or instrumented —
prefer a thin abstraction so ranking/query stay JVM-pure and only the DB layer needs Android).

---

## 3. Ordered commits (test-first)

Each step: **red→green pure core (or data) first, then wiring, then the quality gate.** One logical
unit per commit; keep commits small and phase-tagged (`feat: phase 7x — …`).

### 7a — `core/search` query + ranking (pure)
1. `VaultSearchModels` (candidate, ranked result, `bestField`, snippet, highlight segment).
2. `Fts5Query.build(raw): String?` — tokenize on whitespace; per token → quoted phrase; strip
   `"`,`(`,`)`,`\`; drop operator tokens; implicit AND; `null`/empty when no usable tokens. **Tests.**
3. `SearchRanker` — tier boosts, `bestField`, `matchCount`, `compareVaultSearchNotes`
   (score desc, bestField rank, uri). Bounded Levenshtein helper (max dist 1 if len ≤5 else 2; query
   len ≥4). **Tests.**
4. `VaultSearchHighlight` — first matching body line (full query, else any token len ≥3), ≤160 chars,
   1-based line number; `vaultSearchHighlightSegments`. **Tests.**

### 7b — `data/search` index (Android SQLite)
5. `core/repository/VaultSearchRepository` interface: `search(query, vaultInstanceId): Result<…>`,
   `maintain(config, filesDir)`, status flags (`indexReady`, `bodiesIndexReady`), `touchPaths(paths)`.
6. `VaultSearchDatabase` (`SQLiteOpenHelper`): FTS5 virtual table
   `vault_search_notes(uri UNINDEXED, rel_path, title, filename, body)`, tokenizer
   `unicode61 remove_diacritics 2`; a `schema_version` / `meta` table; index file at
   `filesDir/vault-search-index/{sha1(canonicalBase)}.sqlite`; **rebuild on schema mismatch**.
7. `VaultSearchIndexer`: enumerate eligible files (`VaultVisibility` + `MarkdownNoteScanner`-style
   walk); **phase 1** insert titles (`body=''`) → set `indexReady`; **phase 2** fill bodies → set
   `bodiesIndexReady`. Incremental reconcile diff by `(size, lastModified)`; `touchPaths` marks dirty.
   **Tests** (temp DB): insert/query, reconcile add/modify/delete, schema-version rebuild.
8. `SqliteVaultSearchRepository`: run `Fts5Query` MATCH (BM25 pre-filter, cap ~100) → `SearchRanker`
   → cap (initial 50 / final 150). Rotate `vaultInstanceId` on full rebuild / new DB / base-hash change.

### 7c — use cases
9. `SearchVault(repo)` — guards empty query, returns ranked results + status.
10. `MaintainVaultSearchIndex(repo)` — warm/reconcile entry point; idempotent; non-blocking.
11. Hook `touchPaths` into inbox **create/write/delete** side-effects (extend the Phase 2 mutation
    callbacks already wired in `App`/route layer), async and non-blocking. **Tests** for both.

### 7d — `feature/search` + `app` wiring
12. `SearchUiState` (Idle/Opening/Searching/Results/NoMatches/Error + partial-body footer hint).
13. `SearchViewModel`: **260 ms debounce**, cancel prior search, **hold previous results 100 ms** on
    query change, stale-event guard (`searchId` + `vaultInstanceId`), trigger `MaintainVaultSearchIndex`
    on open. **Tests** with `StandardTestDispatcher` (debounce, cancellation, stale drop).
14. `SearchScreen` + `SearchResultRow`: query field, status lines (*Opening search index…*,
    *Searching…*, *N notes found*, partial-index footer; empty hint *"Type to search markdown in the
    vault."*; *"No matches."* when ready). Row = highlighted title/path + optional `{lineNumber} · {text}`
    snippet (≤2 lines); tap → `AppRoute.note(...)`.
15. Route + nav: add `AppRoute.SEARCH`, an `AppShell` entry (search affordance), and an `app/SearchRoute.kt`
    extracted like `AppTodayHubRoute` (keep `App.kt` under its 440 baseline — extract, don't inline).
    Wire DI through `MainActivity → AppRoot → App`.

### 7e — index warm lifecycle
16. Warm after vault session exists + on search open; foreground 5-minute reconcile via the existing
    `ProcessLifecycleOwner` ON_START hook in `App` (reuse the sync poller pattern). Background
    WorkManager **explicitly deferred** — leave a TODO + spec note.

---

## 4. Quality gate (run before each commit; full gate before finishing)

```bash
./scripts/check-module-budgets.sh
./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest
```

Resolve every violation locally. Use `:app:ktlintFormat` only after reviewing the diff. If a module
split is intentional, refresh caps via `./scripts/update-module-budget-baseline.sh` and commit
`scripts/module-budget-baseline.json`.

---

## 5. Risks & mitigations

1. **Android-only SQLite in JVM tests.** Keep `core/search` 100% pure (no `android.database`); confine
   Android types to `data/search`. Test the DB layer with Robolectric or instrumented tests; do not
   pull SQLite into the JVM ranking tests.
2. **Module budget.** Ranker + indexer are the likely 400-line offenders — the split in §1 is
   deliberate; do not merge query/rank/snippet into one file.
3. **`App.kt` baseline (440).** Already near cap after Phase 6; the search route **must** be a
   separate `SearchRoute.kt`, mirroring `AppTodayHubRoute.kt`.
4. **No background sync rule.** Foreground-only reconcile; WorkManager stays out of the PoC. Document
   the deferral in the spec if revisited.
5. **Stale results / vault switch.** `vaultInstanceId` must rotate on rebuild/new DB/base-hash change;
   the ViewModel drops events whose `searchId`/`vaultInstanceId` don't match the active request.
6. **FTS5 availability.** Bundled SQLite supports FTS5 on all supported API levels; add a guarded
   rebuild path if `CREATE VIRTUAL TABLE … USING fts5` ever fails (treat as schema mismatch → rebuild).

---

## 6. Definition of done

- All `core/search` and `data/search` tests pass (vectors copied from notebox).
- Search screen reachable from the shell; typing returns ranked, highlighted results; tap opens the
  reader; status/empty/no-match strings match §12.3.
- Index warms in the foreground, reconciles incrementally, and survives schema changes via rebuild.
- Full quality gate green; module budgets respected; one PR for Phase 7 (per plan §8 cadence).

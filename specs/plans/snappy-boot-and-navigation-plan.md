# Snappy boot & navigation plan

Performance plan for two perceived-latency problems: the post-splash spinner on
cold start, and slow wiki-link navigation. Goal is a *snappy* feel through smart
caching and prefetching, never showing work the user does not need to see.

Extends [boot-optimization.md](../architecture/boot-optimization.md). That doc
currently lists **"Full note-registry cache outside Inbox"** as out of scope —
this plan promotes it to in scope, because it is the single shared root cause of
both problems.

## Problem analysis

### 1. Post-splash spinner

Already optimistic today: the gate (`AppGateViewModel` + `GateFingerprintComputer`)
emits `Ready` instantly from a fingerprint cache, and the inbox uses
stale-while-revalidate via `FileInboxSnapshotStore`. What remains:

- `MarkdownNoteScanner.extractTitleAndSnippet` calls `file.readText()` on **every**
  file in the vault (full content) only to extract a title + first non-blank line.
- The cold-start revalidation therefore re-walks the whole vault with full reads,
  which easily exceeds the 300 ms `showRefreshIndicator` debounce — so the refresh
  spinner (`InboxScreen.kt:128`) appears *after* the splash. This is the "mega
  spinner".

### 2. Slow wiki-link navigation (biggest offender)

`LoadNoteForReading` calls `registryRepository.refresh()` on **every** note open
(`LoadNoteForReading.kt:34`) = a full vault walk + full read of every file, then
reads the target file. Every wiki-link tap re-scans the entire vault before any
content appears. There is zero caching in the reader path.

**Common root cause:** `NoteRegistry` is never retained. Inbox, reader, and today
hub each rebuild it from scratch with expensive full-file reads.

## Architecture

One shared in-memory registry cache, a cheaper incremental scanner, and a small
content prefetch cache. All caching lives behind the existing repository
interfaces in `data/` — UI keeps receiving state + callbacks, and use-case
*signatures* are unchanged (only their wiring swaps the raw repo for a caching
decorator). This respects [ADR-001](../adr/001-hybrid-layering-and-feature-slices.md).

```
MainActivity (manual DI, process-scoped)
  ├── NoteRegistryCache        (new singleton)
  │     ├── StateFlow<NoteRegistry?>      in-memory, shared by all consumers
  │     ├── persisted full-registry snapshot (fingerprint-keyed)
  │     └── incremental scan (per-file memo on path + mtime + size)
  └── NoteContentCache         (new bounded LRU behind FileNoteContentRepository)
```

## Phases

Recommended order: **1 → 2 → 4 → 3**. Phases 1+2 together fix wiki-link latency
*and* boot revalidation at lowest risk; 4 makes navigation feel instant; 3 is the
finishing touch against post-splash reflow.

### Measurement / instrumentation (pre-work) — done

Throwaway `Log.i` timings under tag **`EskerraPerf`** (remove after baseline is
recorded):

| Event | Where | Fields |
|---|---|---|
| `vault_scan` | `MarkdownNoteScanner.scan` | `durationMs`, `noteCount` |
| `inbox_revalidation` | `LoadInboxSummaries` | `durationMs`, `inboxCount` |
| `note_open` | `LoadNoteForReading` | `durationMs`, `noteId`, `registryMs`, `contentMs` |

**Capture a before baseline** on a representative vault (large enough that boot
shows the post-splash refresh spinner and wiki-links feel slow):

```bash
adb logcat -s EskerraPerf
```

1. Cold start the app (force-stop first) — note `inbox_revalidation` and nested
   `vault_scan` duration. Compare `inbox_revalidation` to the 300 ms refresh
   indicator debounce (`InboxViewModel.REFRESH_INDICATOR_DELAY_MS`).
2. Open a note via wiki-link — note `note_open` total and `registryMs` vs
   `contentMs` (expect `registryMs` ≫ `contentMs` before Phase 1+2).
3. Record numbers in this file or a scratch note before starting Phase 1.

Utility: [`SnappyPerfLog.kt`](../../app/src/main/java/com/eskerra/go/data/perf/SnappyPerfLog.kt).

### Phase 1 — Cheaper incremental scanner

- `extractTitleAndSnippet`: buffered read with **early exit** once title + first
  non-blank line are found, instead of `readText().lines()` over the whole file.
- Per-file memoization: pass the previous `NoteRegistry` into the scan; for files
  whose `mtime` + `size` are unchanged, reuse the existing `NoteSummary` **without
  reading**. The walk still `stat`s every file (cheap); reads become O(changed
  files).
- *Pro:* revalidation drops below the 300 ms threshold → spinner no longer shows on
  boot. *Con:* mtime granularity; renames = delete+add (correct — the walk
  re-derives the set each pass).

### Phase 2 — Shared in-memory registry cache

- New `NoteRegistryCache` (singleton in `MainActivity`, like the other deps),
  thread-safe (Mutex):
  - `current()`: in-memory registry → else persisted snapshot → else null.
  - `refresh()`: incremental scan, update StateFlow, persist snapshot.
- `LoadNoteForReading`, `LoadInboxSummaries`, `LoadTodayHub` consume the cache
  instead of calling `registryRepository.refresh()` directly.
- Invalidate at the existing mutation points (`SaveNote`, `CreateInboxNote`,
  `DeleteInboxNotes`, and manual-sync success — these already call
  `markInboxNotesChanged` / `touchVaultSearchPaths`).
- *Pro:* wiki-link navigation drops from O(whole vault) to O(1 file). *Con:*
  externally-changed files (git pull) only surface after revalidation — acceptable
  in the PoC (no background sync) provided we invalidate after manual sync.

### Phase 3 — Boot: no reflow after splash

- Persist the **full** registry snapshot (generalize `FileInboxSnapshotStore`;
  inbox becomes `registry.inboxSummaries` — one source of truth).
- On revalidation, **diff** against the current registry; if equal → no state emit
  → no reflow. Only a real diff is allowed to reflow (the one permitted "jump").
- Suppress the refresh indicator while serving from a trusted snapshot; keep the
  splash up until the cached inbox has rendered one frame (`withFrameNanos` hook
  already exists in `AppLaunchSettled.kt`).

### Phase 4 — Instant wiki-links via prefetch

- When a note's `Content` state is ready, in a background coroutine parse its
  `[[wikilinks]]` + relative `.md` links (`WikiLinkParser` already exists), resolve
  them via the cached registry, and prefetch target content into a bounded LRU
  (`NoteContentCache`, ~8–16 entries).
- `LoadNoteForReading` checks the content cache first → hit = instant (registry is
  already in memory). Cancel prefetch on dispose, run off the main thread, bound
  concurrency.
- *Pro:* tap = instant. *Con:* extra upfront I/O, bounded by LRU size and only for
  the visible note.

## Verification & quality

- Measure before/after on a representative vault (scan time + navigation latency)
  to prove the win.
- Unit test per slice (ADR requirement): scanner memoization, SWR diff (no reflow
  when equal), prefetch resolution.
- Quality gate: `ktlintCheck`, `lintDebug`, `testDebugUnitTest`, module budgets
  (<400 lines per new file).
- Update [boot-optimization.md](../architecture/boot-optimization.md): move
  "Full note-registry cache outside Inbox" out of the "Out of scope" list.

## Recommended model per work item

Heuristic: strongest model for concurrency / cache-invalidation correctness;
faster, cheaper models for mechanical edits, scaffolding, and tests with a clear
pattern. ("Composer 2.5" = fast mechanical edits; downgrade freely where the
change is local and well-specified.)

| Work item | Recommended model | Why |
|---|---|---|
| Measurement / instrumentation (pre-work) | Haiku 4.5 / Composer 2.5 | Trivial, local, throwaway timing logs. |
| Phase 1 — scanner early-exit | Sonnet 4.6 (medium) | Single function, but correctness-sensitive (snippet/title parity). |
| Phase 1 — per-file memoization | Sonnet 4.6 (high) | mtime/size keying, delete/rename edge cases. |
| Phase 2 — `NoteRegistryCache` (Mutex, SWR, persist) | **Opus 4.8** | Concurrency + invalidation correctness; the architectural core. |
| Phase 2 — rewire use cases to the cache | Sonnet 4.6 (medium) | Mechanical decorator swap once the cache exists. |
| Phase 2 — invalidation at mutation points | Sonnet 4.6 (high) | Must catch every write path; subtle if missed. |
| Phase 3 — full-registry snapshot codec | Composer 2.5 / Sonnet (medium) | Pattern already set by `FileInboxSnapshotStore`. |
| Phase 3 — SWR diff + suppress reflow | Sonnet 4.6 (high) | UI-state subtlety; easy to reintroduce flicker. |
| Phase 4 — `NoteContentCache` LRU | Sonnet 4.6 (medium) | Standard bounded LRU behind the repo. |
| Phase 4 — prefetch (lifecycle, cancel, concurrency) | **Opus 4.8** | Coroutine cancellation + leak avoidance is the trickiest part. |
| Unit tests (all phases) | Composer 2.5 / Sonnet (medium) | Clear pattern from existing tests. |
| Doc updates (this file + boot-optimization.md) | Haiku 4.5 | Prose only. |

## Out of scope

- Background sync (unchanged from PoC contract).
- Multi-workspace snapshot keys.
- Reworking the SQLite FTS search index (separate subsystem).

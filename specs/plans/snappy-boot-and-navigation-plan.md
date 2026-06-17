# Snappy boot & navigation plan

Performance plan for two perceived-latency problems: the post-splash spinner on
cold start, and slow wiki-link navigation. Goal is a *snappy* feel through smart
caching and prefetching, never showing work the user does not need to see.

**Status: implemented** on branch `need-for-speed`. Architecture details live in
[boot-optimization.md](../architecture/boot-optimization.md).

## Problem analysis

### 1. Post-splash spinner

Already optimistic today: the gate (`AppGateViewModel` + `GateFingerprintComputer`)
emits `Ready` instantly from a fingerprint cache, and the inbox uses
stale-while-revalidate via `FileInboxSnapshotStore`. What remained before this work:

- `MarkdownNoteScanner.extractTitleAndSnippet` called `file.readText()` on **every**
  file in the vault (full content) only to extract a title + first non-blank line.
- The cold-start revalidation therefore re-walked the whole vault with full reads,
  which easily exceeded the 300 ms `showRefreshIndicator` debounce — so the refresh
  spinner (`InboxScreen.kt:128`) appeared *after* the splash. This is the "mega
  spinner".

### 2. Slow wiki-link navigation (biggest offender)

`LoadNoteForReading` called `registryRepository.refresh()` on **every** note open
= a full vault walk + full read of every file, then reads the target file. Every
wiki-link tap re-scanned the entire vault before any content appeared. There was
zero caching in the reader path.

**Common root cause:** `NoteRegistry` was never retained. Inbox, reader, and today
hub each rebuilt it from scratch with expensive full-file reads.

## Architecture

One shared in-memory registry cache, a cheaper incremental scanner, and a small
content prefetch cache. All caching lives behind the existing repository
interfaces in `data/` — UI keeps receiving state + callbacks, and use-case
*signatures* are unchanged (only their wiring swaps the raw repo for a caching
decorator). This respects [ADR-001](../adr/001-hybrid-layering-and-feature-slices.md).

```
MainActivity (manual DI, process-scoped)
  ├── CoalescingNoteRegistryRepository → FileNoteRegistryRepository
  ├── NoteRegistryCache
  │     ├── StateFlow<NoteRegistry?>      in-memory, shared by all consumers
  │     ├── persisted full-registry snapshot (fingerprint-keyed)
  │     └── incremental scan (per-file memo on path + mtime + size)
  └── NoteContentCache         (bounded LRU behind FileNoteContentRepository)
```

## Phases — done

### Measurement / instrumentation (pre-work) — done

Throwaway `Log.i` timings under tag **`EskerraPerf`** (remove after baseline is
recorded):

| Event | Where | Fields |
|---|---|---|
| `vault_scan` | `MarkdownNoteScanner.scan` | `durationMs`, `noteCount` |
| `inbox_revalidation` | `LoadInboxSummaries` | `durationMs`, `inboxCount` |
| `note_open` | `LoadNoteForReading` | `durationMs`, `noteId`, `registryMs`, `contentMs` |

Utility: [`SnappyPerfLog.kt`](../../app/src/main/java/com/eskerra/go/data/perf/SnappyPerfLog.kt).

### Phase 1 — Cheaper incremental scanner — done

- Buffered read with **early exit** once title + first non-blank line are found.
- Per-file memoization on `mtime` + `size`; unchanged files skip `read`.

### Phase 2 — Shared in-memory registry cache — done

- `NoteRegistryCache` (singleton in `MainActivity`): `current()`, `refresh()`,
  `invalidate()`.
- Read use cases consume the cache; mutation paths invalidate + refresh.
- `CoalescingNoteRegistryRepository` (from `main`) deduplicates parallel home-load scans.

### Phase 3 — Boot: no reflow after splash — done

- Full registry snapshot codec + `FileNoteRegistrySnapshotStore`.
- Inbox SWR diff: equal revalidation → no list replacement → no reflow.
- Fast revalidation keeps `showRefreshIndicator` off (300 ms debounce).

*Follow-up (not blocking):* consolidate inbox cold-start onto
`registry.inboxSummaries` and retire the separate `FileInboxSnapshotStore` file.

### Phase 4 — Instant wiki-links via prefetch — done

- `NoteContentCache` (LRU, default 16 entries).
- `PrefetchLinkedNotes` warms linked targets after a note opens.
- `PrefetchLinkTargets` resolves wiki + relative `.md` links in-memory.

## Verification & quality — done

Unit tests per slice (ADR requirement):

| Area | Test file |
|---|---|
| Scanner memoization + early exit | `MarkdownNoteScannerTest` |
| Registry cache SWR + persist | `NoteRegistryCacheTest` |
| Scan coalescing | `CoalescingNoteRegistryRepositoryTest` |
| Full-registry snapshot codec | `NoteRegistrySnapshotCodecTest`, `FileNoteRegistrySnapshotStoreTest` |
| Inbox SWR / no reflow | `InboxViewModelTest` (`revalidation_withUnchangedNotes_doesNotReplaceNotesList`, `fastRevalidationWithTrustedSnapshot_doesNotShowRefreshIndicator`) |
| Inbox snapshot wrapper | `LoadInboxSummariesCachedTest` |
| Content cache + note open | `NoteContentCacheTest`, `LoadNoteForReadingTest` |
| Prefetch resolution | `PrefetchLinkTargetsTest`, `PrefetchLinkedNotesTest` |
| Launch settled | `AppLaunchSettledTest` |

Quality gate: `ktlintCheck`, `lintDebug`, `testDebugUnitTest`, module budgets.

Docs: [boot-optimization.md](../architecture/boot-optimization.md) updated (full
note-registry cache in scope).

## Out of scope

- Background sync (unchanged from PoC contract).
- Multi-workspace snapshot keys.
- Reworking the SQLite FTS search index (separate subsystem).
- Retiring `FileInboxSnapshotStore` in favor of registry-only inbox cold start.

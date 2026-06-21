# Boot optimization

## Purpose

Reduce cold-start latency by avoiding blocking network Git work and repeated filesystem scans before the main shell is visible.

## App gate

The root gate resolves in three layers:

1. **DataStore read** — load persisted [WorkspaceConfig](app/src/main/java/com/eskerra/go/core/model/WorkspaceConfig.kt).
2. **Local gate** — [resolveAppGateState](app/src/main/java/com/eskerra/go/data/workspace/AppGateResolver.kt) checks path safety, workspace directory presence, and a valid `.git` tree. Runs on `Dispatchers.IO`.
3. **Optimistic fingerprint** — when stored [GateFingerprint](app/src/main/java/com/eskerra/go/core/model/GateFingerprint.kt) matches [GateFingerprintComputer.compute](app/src/main/java/com/eskerra/go/data/workspace/GateFingerprintComputer.kt), emit `Ready` immediately and validate on a background job.

### Fingerprint rules

Fingerprint input (no network):

- `relativePath`
- `setupCompletedAtEpochMs`
- configured `branch`
- `.git/HEAD` content
- `refs/heads/<branch>` object id when present

Persist fingerprint after:

- successful local gate resolution
- workspace setup complete
- branch/config reconciliation updates

Clear fingerprint when:

- async validation fails (gate downgrades to recoverable `NeedsSetup`)
- workspace metadata is cleared

### Branch reconciliation

[ReconcileWorkspaceSyncBranch](app/src/main/java/com/eskerra/go/core/usecase/ReconcileWorkspaceSyncBranch.kt) runs **after** the gate is `Ready`, from [App.kt](app/src/main/java/com/eskerra/go/app/App.kt). It must not block the root spinner. Manual sync still re-runs branch alignment before commit.

## Note registry cache

Process-scoped [NoteRegistryCache](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt) in [MainActivity.kt](app/src/main/java/com/eskerra/go/MainActivity.kt) is the single source of truth for indexed note summaries across inbox, reader, and Today Hub.

Layers:

1. **In-memory** — `StateFlow<NoteRegistry?>`; lock-free reads while a refresh is in flight.
2. **Persisted snapshot** — [FileNoteRegistrySnapshotStore](app/src/main/java/com/eskerra/go/data/notes/FileNoteRegistrySnapshotStore.kt) under `filesDir/cache/note_registry_snapshot.json`, keyed by workspace fingerprint.
3. **Incremental scan** — [MarkdownNoteScanner](app/src/main/java/com/eskerra/go/data/notes/MarkdownNoteScanner.kt) reuses per-file summaries when `mtime` + `sizeBytes` are unchanged; [CoalescingNoteRegistryRepository](app/src/main/java/com/eskerra/go/data/notes/CoalescingNoteRegistryRepository.kt) deduplicates concurrent vault walks on home load.

Snapshot notes persist `sizeBytes` in [SnapshotNoteJsonCodec](app/src/main/java/com/eskerra/go/data/notes/SnapshotNoteJsonCodec.kt) (legacy snapshots without the field decode as `0`). After cold start, restored summaries therefore participate in incremental scan instead of forcing a full re-read of every file.

Read paths use [current](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt) when possible and refresh incrementally in the background:

- [LoadInboxSummaries](app/src/main/java/com/eskerra/go/core/usecase/LoadInboxSummaries.kt) — always refreshes (inbox SWR).
- [LoadNoteForReading](app/src/main/java/com/eskerra/go/core/usecase/LoadNoteForReading.kt) — serves cached registry on the critical path; cold miss awaits refresh; warm hit dispatches background refresh.
- [LoadTodayHub](app/src/main/java/com/eskerra/go/core/usecase/LoadTodayHub.kt) — uses `current()` for snapshot restore, then background revalidation.

Write paths (`SaveNote`, `CreateInboxNote`, `DeleteInboxNotes`, successful manual sync) evict affected content where applicable and call incremental [refresh](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt) without [invalidate](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt), so the in-memory registry remains the memo base for the post-write scan.

[refresh](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt) persists a snapshot via [FileNoteRegistrySnapshotStore](app/src/main/java/com/eskerra/go/data/notes/FileNoteRegistrySnapshotStore.kt) on success; snapshot write failures are best-effort (`runCatching`) and do not fail the refresh.

Wiki-link opens use [NoteContentCache](app/src/main/java/com/eskerra/go/data/notes/NoteContentCache.kt) (bounded LRU, workspace-fingerprint scoped, generation-guarded against post-evict TOCTOU) with background [PrefetchLinkedNotes](app/src/main/java/com/eskerra/go/core/usecase/PrefetchLinkedNotes.kt). Prefetch warms both content and the shared [ParsedMarkdownCache](app/src/main/java/com/eskerra/go/data/notes/ParsedMarkdownCache.kt) (parse on `Dispatchers.Default`, max concurrency 4) so a warm link tap can atomic-swap in [VaultMarkdownView](app/src/main/java/com/eskerra/go/ui/markdown/VaultMarkdownView.kt).

## Inbox snapshot cache

[FileInboxSnapshotStore](app/src/main/java/com/eskerra/go/data/notes/FileInboxSnapshotStore.kt) persists the last inbox summary list under `filesDir/cache/inbox_snapshot.json`, keyed by workspace fingerprint. This remains the cold-start path for the inbox list until a later consolidation to `registry.inboxSummaries` only.

- **Cold start:** show cached list with `isRefreshing = true`, scan in background, then replace only when data differs (see SWR below).
- **No cache:** keep existing full-screen inbox loading behavior.
- **Scan failure with cache:** keep cached list visible; do not replace with error.
- **Invalidation:** fingerprint mismatch ignores stale file; successful scan overwrites snapshot.
- **Empty inbox:** a valid snapshot uses `"summaries":[]` and round-trips to an empty list (cache hit on next cold start).

Inbox ordering after refresh remains last-modified descending per [app-contract.md](app-contract.md).

### Inbox stale-while-revalidate (SWR)

[InboxViewModel](app/src/main/java/com/eskerra/go/app/InboxViewModel.kt) compares the refreshed inbox list to the list already on screen. When equal, it clears `isRefreshing` without replacing the `notes` list reference — no reflow after splash.

The debounced `showRefreshIndicator` (~300 ms) stays off when revalidation finishes quickly (typical with incremental scan + cache hit).

## Launch UX

Cold start keeps the Android splash screen (inset foreground via `drawable/ic_splash_logo`, on `#281943`) until launch is **settled**, then dismisses in one step to shell + content.

**Launch settled** means:

1. App gate is not `Loading` (`NeedsSetup` or `Ready`).
2. When `Ready`, inbox UI is `Content`, `Empty`, or `Error` — not full-screen `Loading`.
3. When `Ready`, Today Hub UI is `Content`, `Empty`, or `Error` — not `Loading` (wired via `onTodayHubUiStateChanged` from [AppInboxRoute.kt](app/src/main/java/com/eskerra/go/app/AppInboxRoute.kt) through [AppRoot.kt](app/src/main/java/com/eskerra/go/app/AppRoot.kt)).
4. At least one layout frame has passed (`awaitFrame()`), plus a minimum hold of ~150ms to avoid subliminal flicker on fast devices.

Implementation:

- [MainActivity.kt](app/src/main/java/com/eskerra/go/MainActivity.kt) — `setKeepOnScreenCondition` until [AppLaunchSettled.kt](app/src/main/java/com/eskerra/go/app/AppLaunchSettled.kt) fires.
- [TodayHubViewModel.restoreSnapshot()](app/src/main/java/com/eskerra/go/app/TodayHubViewModel.kt) uses `registryCache.current()` (persisted snapshot) instead of bailing when the in-memory registry is null; background revalidation stays silent when a snapshot is already shown.
- Gate `Loading` renders an empty surface (no spinner); splash covers it.
- Shell sync FAB maps `SyncUiState.Loading` to no indicator (quiet shell refresh per [sync-hardening-and-recovery.md](sync-hardening-and-recovery.md)).
- Inbox background rescan uses debounced `showRefreshIndicator` (~300ms) so fast cache-hit refreshes stay silent.

## Out of scope

- WorkManager/AlarmManager scheduled sync
- Multi-workspace snapshot keys

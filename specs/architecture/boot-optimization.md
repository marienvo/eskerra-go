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
3. **Incremental scan** — [MarkdownNoteScanner](app/src/main/java/com/eskerra/go/data/notes/MarkdownNoteScanner.kt) reuses per-file summaries when `mtime` + `size` are unchanged; [CoalescingNoteRegistryRepository](app/src/main/java/com/eskerra/go/data/notes/CoalescingNoteRegistryRepository.kt) deduplicates concurrent vault walks on home load.

Read paths (`LoadInboxSummaries`, `LoadNoteForReading`, `LoadTodayHub`) consume the cache. Write paths (`SaveNote`, `CreateInboxNote`, `DeleteInboxNotes`, successful manual sync) call [invalidate](app/src/main/java/com/eskerra/go/data/notes/NoteRegistryCache.kt) then refresh.

Wiki-link opens also use [NoteContentCache](app/src/main/java/com/eskerra/go/data/notes/NoteContentCache.kt) (bounded LRU) with background [PrefetchLinkedNotes](app/src/main/java/com/eskerra/go/core/usecase/PrefetchLinkedNotes.kt) for linked targets.

## Inbox snapshot cache

[FileInboxSnapshotStore](app/src/main/java/com/eskerra/go/data/notes/FileInboxSnapshotStore.kt) persists the last inbox summary list under `filesDir/cache/inbox_snapshot.json`, keyed by workspace fingerprint. This remains the cold-start path for the inbox list until a later consolidation to `registry.inboxSummaries` only.

- **Cold start:** show cached list with `isRefreshing = true`, scan in background, then replace only when data differs (see SWR below).
- **No cache:** keep existing full-screen inbox loading behavior.
- **Scan failure with cache:** keep cached list visible; do not replace with error.
- **Invalidation:** fingerprint mismatch ignores stale file; successful scan overwrites snapshot.
- **Empty inbox:** a valid snapshot uses `"summaries":[]` and round-trips to an empty list (cache hit on next cold start).

Inbox ordering after refresh remains last-modified descending per [poc-contract.md](poc-contract.md).

### Inbox stale-while-revalidate (SWR)

[InboxViewModel](app/src/main/java/com/eskerra/go/app/InboxViewModel.kt) compares the refreshed inbox list to the list already on screen. When equal, it clears `isRefreshing` without replacing the `notes` list reference — no reflow after splash.

The debounced `showRefreshIndicator` (~300 ms) stays off when revalidation finishes quickly (typical with incremental scan + cache hit).

## Launch UX

Cold start keeps the Android splash screen (inset foreground via `drawable/ic_splash_logo`, on `#281943`) until launch is **settled**, then dismisses in one step to shell + content.

**Launch settled** means:

1. App gate is not `Loading` (`NeedsSetup` or `Ready`).
2. When `Ready`, inbox UI is `Content`, `Empty`, or `Error` — not full-screen `Loading`.
3. At least one layout frame has passed (`awaitFrame()`), plus a minimum hold of ~150ms to avoid subliminal flicker on fast devices.

Implementation:

- [MainActivity.kt](app/src/main/java/com/eskerra/go/MainActivity.kt) — `setKeepOnScreenCondition` until [AppLaunchSettled.kt](app/src/main/java/com/eskerra/go/app/AppLaunchSettled.kt) fires.
- Gate `Loading` renders an empty surface (no spinner); splash covers it.
- Shell sync FAB maps `SyncUiState.Loading` to no indicator (quiet shell refresh per [sync-hardening-and-recovery.md](sync-hardening-and-recovery.md)).
- Inbox background rescan uses debounced `showRefreshIndicator` (~300ms) so fast cache-hit refreshes stay silent.

See also [snappy-boot-and-navigation-plan.md](../plans/snappy-boot-and-navigation-plan.md) for the full performance plan and test map.

## Out of scope

- Background sync
- Multi-workspace snapshot keys

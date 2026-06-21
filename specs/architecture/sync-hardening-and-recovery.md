# Sync hardening and recovery (Step 9 Slice 4)

## Purpose

Manual HTTPS sync works after Step 9 Slices 1–3. Slice 4 hardens behavior before using a valuable personal notes repository: single-flight sync, preflight clarity, staged-index safety, interrupted-Git recovery, partial registry-refresh success, safe diagnostics, and non-secret last-sync persistence.

## Product rules

### Write paths

- **Inbox notes:** user-editable; persisted to disk on save; committed via manual vault sync.
- **Podcast markdown under `General/`:** catalog is read-only except checkbox writes (mark-as-played) and native RSS refresh output. Mark-as-played auto-commits changed podcast paths only; RSS refresh writes markdown then delegates to the vault sync engine (see [Git sync channels](#git-sync-channels)).
- **All other vault paths:** read-only from the app UI (remote integration during vault sync may update them locally).

### Manual vault sync

- App-initiated manual sync (`ManualSyncNow`) commits **all safe local working-tree changes**, not only `Inbox/`.
- The only hard stop before commit: unsafe paths (`.git` internals, `..`).
- Commit message: `Sync local changes from Eskerra Go`.
- Integration: fast-forward when purely behind remote; **auto-merge** when histories diverged, writing sidecar copies `path (conflict yyyy-MM-dd HH.mm.ss).ext` where remote wins the canonical file. Returned in `SyncResult.conflictCopies`.
- Push: retry up to three integrate+push cycles when the remote rejects a racing push.
- **Recovery before sync:** if merge, cherry-pick, revert, or rebase is in progress, vault sync calls `abortInProgressOperation` (rebase abort; otherwise `reset --hard` to HEAD) and continues. **Trade-off:** in-progress conflict-resolution work on disk may be lost rather than leaving the user blocked.

### Git sync channels

All JGit mutations share one process-wide **mutex** so vault sync and podcast auto-sync never overlap (the working tree is a single fragile resource).

| Channel | Trigger | Stage | Integration | Push |
| --- | --- | --- | --- | --- |
| Manual vault sync | User taps sync | All safe local changes | FF when behind; auto-merge on divergence | Yes, with retry |
| Podcast RSS refresh | Pull-to-refresh | RSS writes `General/`; then delegates to `ManualSyncNow` | Same as vault sync | Same as vault sync |
| Podcast mark-as-played | Checkbox | Changed podcast paths under `General/` only | **Fast-forward only** | Best-effort; `pendingPush` on divergence/offline |

Commit messages (examples):

| Channel | Commit message (example) |
| --- | --- |
| Manual vault sync | `Sync local changes from Eskerra Go` |
| Podcast mark-as-played | `Mark podcast episodes played` |
| Podcast RSS refresh | Uses vault sync engine after RSS write (no separate commit message) |

**Podcast mark-as-played flow** (`SyncPodcastChange`):

1. Write markdown to disk.
2. Acquire shared git mutex.
3. Stage only the paths changed in this operation.
4. Commit (skip if nothing to commit).
5. `fetch` origin; fast-forward local branch if behind.
6. `push` if ahead of remote.
7. On offline or non-fast-forward divergence: keep the local commit, record pending push state, retry on the next operation. **Never** auto-merge, rebase, or reset.

**Podcast RSS refresh flow** (`SyncPodcastVaultRefresh` → `SyncPodcastChangesViaVaultSync` → `ManualSyncNow`):

1. Fetch RSS feeds and merge markdown into `General/` on disk.
2. Acquire shared git mutex via vault sync engine.
3. Commit all safe pending local changes, integrate remote (FF or auto-merge), push.

Podcast auto-sync is **foreground work** tied to user actions, not a background scheduler.

### Integration policy

- **Vault sync** (manual button and RSS refresh): fast-forward when behind; auto-merge with conflict sidecars when diverged.
- **Podcast mark-as-played:** fast-forward only; divergence leaves a local commit with `pendingPush`.
- **Shell status indicator:** may show `Diverged` before the user syncs; vault sync resolves divergence on the next successful run.

## Reentrancy

- **UI/ViewModel:** ignore duplicate `syncNow()` while `SyncUiState.Syncing`. Do not cancel and restart an in-flight sync on double-tap.
- **UI/ViewModel:** `syncNow()` cancels any in-flight status `loadJob` before starting sync so a completing refresh cannot overwrite `SyncUiState.Syncing`.
- **Use case:** `ManualSyncNow` holds the shared git mutex. A concurrent invoke returns `SyncError.SyncAlreadyRunning`.
- **Editor/save:** local editing and saving remain allowed during sync (no global UI lock).

## Staged index safety (vault sync)

Before commit, vault sync stages all safe working-tree changes via `stageAllChanges`. Only unsafe staged paths block sync (`SyncError.UnsafeLocalPath`).

Podcast mark-as-played stages only the podcast paths for that operation and must not leave unrelated paths staged when the mutex is released. Unexpected staged paths outside the operation return `SyncError.UnexpectedStagedChanges`.

## Manual-intervention Git states

- **Vault sync:** recovers automatically via `abortInProgressOperation` before proceeding (see [Manual vault sync](#manual-vault-sync)).
- **Podcast mark-as-played:** refuses when merge, rebase, cherry-pick, revert, or similar operation is in progress. Returns `SyncError.ManualInterventionRequired`. No auto reset, stash, merge, or rebase.

Preflight may report `repoInterventionRequired = true` as informational when an interrupted Git operation is detected; vault sync is still allowed and will recover.

## Partial registry refresh success

When fetch/push/pull/commit completes but `NoteRegistryRepository.refresh` fails, sync returns **success** with `registryRefreshed = false`. UI shows a warning, not a full failure. Last sync outcome is recorded as `PartialSuccess`. Local notes remain available.

## Last sync persistence

DataStore holds **one** latest attempt only:

- `attemptedAtEpochMs`
- `outcome`: Success | PartialSuccess | Failed
- `errorCategory`: safe enum name when failed/partial (e.g. `AuthenticationFailed`), never token or raw exception text

No sync history database.

## Diagnostics

`SafeSyncDiagnostic` may include sanitized host/repo, branch, change counts, ahead/behind, and last safe sync outcome. Must never include token, credential-bearing URL, raw auth headers, raw low-level exceptions, or full local filesystem paths.

## Recovery guidance

Each blocking `SyncError` maps to a short recovery hint via `SyncRecoveryGuidance`. Hints are non-technical and never suggest destructive Git commands.

## Foreground sync-status refresh

- On app start (after the workspace gate is `Ready`) and when the app returns to the foreground, the shell may run a **read-only** remote check: `fetch` to update remote-tracking refs, then local ahead/behind comparison.
- Start with a **local-only** status read for the shell indicator, then run the remote check without forcing `SyncUiState.Loading` so the sync button stays usable while the fetch completes. Use a single status `loadJob` so the local emit completes before the remote fetch starts (no cancel between the two steps).
- This is user-visible foreground work only; it does not commit, pull, or push.
- Debounce rapid foreground refreshes (for example within 30 seconds) to avoid redundant network calls.
- After inbox note create or save, the app may reload **local-only** Git sync status for the shell indicator. This does not fetch remote.

## Out of scope

WorkManager/AlarmManager scheduled sync, inbox sync-on-save, SSH, interactive conflict-resolution UI, full sync history, note deletion/move/rename (inbox delete is implemented separately).

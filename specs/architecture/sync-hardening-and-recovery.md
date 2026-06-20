# Sync hardening and recovery (Step 9 Slice 4)

## Purpose

Manual HTTPS sync works after Step 9 Slices 1–3. Slice 4 hardens behavior before using a valuable personal notes repository: single-flight sync, preflight clarity, staged-index safety, manual-intervention detection, partial registry-refresh success, safe diagnostics, and non-secret last-sync persistence.

## Product rules

### Write paths

- **Inbox notes:** user-editable; persisted to disk on save; committed via manual sync only.
- **Podcast markdown under `General/`:** catalog is read-only except checkbox writes (mark-as-played) and native RSS refresh output. Each logical operation auto-commits changed podcast paths and pushes when possible (see [Git sync channels](#git-sync-channels)).
- **All other vault paths:** read-only from the app (remote fast-forward may update them during manual sync).

### Manual inbox sync

- App-initiated manual sync commits may contain only **`Inbox/`** changes.
- Remote changes may update files anywhere during fast-forward integration.

### Git sync channels

All JGit mutations share one process-wide **mutex** so manual inbox sync and podcast auto-sync never overlap (the working tree is a single fragile resource).

| Channel | Use case | Stage | Commit message (example) |
| --- | --- | --- | --- |
| Manual inbox sync | User taps sync | `Inbox/` only | `Update inbox notes from Eskerra Go` |
| Podcast auto-sync | Mark-as-played or RSS refresh complete | Changed podcast paths under `General/` only | `Mark podcast episode(s) played` / `Refresh podcast episodes` |

**Podcast auto-sync flow** (per operation):

1. Write markdown to disk.
2. Acquire shared git mutex.
3. Stage only the paths changed in this operation.
4. Commit (skip if nothing to commit).
5. `fetch` origin; fast-forward local branch if behind.
6. `push` if ahead of remote.
7. On offline or non-fast-forward divergence: keep the local commit, record pending push state, retry on the next sync or podcast operation. Never auto-merge, rebase, or reset.

Podcast auto-sync is **foreground work** tied to user actions, not a background scheduler.

### Integration policy

- Fast-forward only for pull/integration.
- Diverged histories return `SyncError.Diverged` / `SyncError.ConflictRisk`; user must repair manually outside the app.

## Reentrancy

- **UI/ViewModel:** ignore duplicate `syncNow()` while `SyncUiState.Syncing`. Do not cancel and restart an in-flight sync on double-tap.
- **UI/ViewModel:** `syncNow()` cancels any in-flight status `loadJob` before starting sync so a completing refresh cannot overwrite `SyncUiState.Syncing`.
- **Use case:** `ManualSyncNow` holds the shared git mutex. A concurrent invoke returns `SyncError.SyncAlreadyRunning`.
- **Editor/save:** local editing and saving remain allowed during sync (no global UI lock).

## Staged index safety (manual inbox sync)

Before commit, manual sync reads staged paths from the Git index. If any staged path is outside `Inbox/` → `SyncError.NonInboxStagedChanges`. If any staged path is unsafe → `SyncError.UnsafeLocalPath`. Manual sync does not auto-unstage and does not call broad `stageAll`.

Podcast auto-sync stages only the podcast paths for that operation and must not leave unrelated paths staged when the mutex is released.

## Manual-intervention Git states

Sync refuses when merge, rebase, cherry-pick, revert, or similar operation is in progress (presence of `MERGE_HEAD`, `rebase-merge`, `rebase-apply`, `CHERRY_PICK_HEAD`, or `REVERT_HEAD`). Returns `SyncError.ManualInterventionRequired`. No auto reset, stash, merge, or rebase.

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

WorkManager/AlarmManager scheduled sync, inbox sync-on-save, SSH, conflict resolution UI, auto merge/rebase/reset/stash, full sync history, note deletion/move/rename (inbox delete is implemented separately).

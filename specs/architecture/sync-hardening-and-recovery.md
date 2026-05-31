# Sync hardening and recovery (Step 9 Slice 4)

## Purpose

Manual HTTPS sync works after Step 9 Slices 1–3. Slice 4 hardens behavior before using a valuable personal notes repository: single-flight sync, preflight clarity, staged-index safety, manual-intervention detection, partial registry-refresh success, safe diagnostics, and non-secret last-sync persistence.

## Product rules (unchanged)

- App-initiated writes remain limited to `Inbox/`.
- App-created commits may contain only `Inbox/` changes.
- Remote changes may update files anywhere during fast-forward integration.
- The rest of the repo remains read-only from the app.

## Reentrancy

- **UI/ViewModel:** ignore duplicate `syncNow()` while `SyncUiState.Syncing`. Do not cancel and restart an in-flight sync on double-tap.
- **Use case:** `ManualSyncNow` holds a `Mutex`. A concurrent invoke returns `SyncError.SyncAlreadyRunning`.
- **Editor/save:** local editing and saving remain allowed during sync (no global lock).

## Staged index safety

Before commit, sync reads staged paths from the Git index. If any staged path is outside `Inbox/` → `SyncError.NonInboxStagedChanges`. If any staged path is unsafe → `SyncError.UnsafeLocalPath`. Sync does not auto-unstage and does not call broad `stageAll`.

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
- This is user-visible foreground work only; it does not commit, pull, push, or schedule background sync.
- Debounce rapid foreground refreshes (for example within 30 seconds) to avoid redundant network calls.

## Out of scope

Background sync, sync-on-save, SSH, conflict resolution UI, auto merge/rebase/reset/stash, full sync history, note deletion/move/rename.

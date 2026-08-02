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
| Vault sync | User taps sync, **or any note write** (see [Automatic vault sync triggers](#automatic-vault-sync-triggers)) | All safe local changes | FF when behind; auto-merge on divergence | Yes, with retry |
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

- **UI/ViewModel:** ignore duplicate `syncNow()` while a sync job is active or `SyncUiState.Syncing`. Do not cancel and restart an in-flight sync on double-tap.
- **UI/ViewModel:** starting a sync cancels any in-flight status `loadJob` first so a completing refresh cannot overwrite `SyncUiState.Syncing`.
- **Use case:** `ManualSyncNow` holds the shared git mutex. A concurrent invoke returns `SyncError.SyncAlreadyRunning`.
- **Editor/save:** local editing and saving remain allowed during sync (no global UI lock).
- **Trigger state** (`syncJob`, `pendingAutoSync`) is owned exclusively by `viewModelScope`/Main. Public entry points marshal onto that scope, so it needs no locks or `@Volatile`. The sync job is created with `CoroutineStart.LAZY` and started only after `syncJob` is assigned, so a second caller cannot slip past the guard before the field is set.

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

Since 2026-08-02 app start and foreground return run a **full auto-sync**, not a read-only remote check
(see below). What remains of the status-refresh path:

- **Local-only reads** (`refreshLocalStatus`, `refreshLocalStatusQuietly`) still serve the shell badge and the sync screen wherever a sync must not or cannot run: no remote configured, blocked preflight, opening the sync screen.
- Quiet refreshes must not force `SyncUiState.Loading`, so the sync button stays usable. Use a single status `loadJob` so a local emit completes before any remote step starts (no cancel between the two steps).
- Any refresh that reaches the network is startup-path work if it can run before launch settles — see [boot-optimization.md](boot-optimization.md#boot-sync-is-gated-on-launch-settlement) for the regression this caused.
- Remote status reads stay debounced (30 s) to avoid redundant network calls; `SyncUiState.Syncing` short-circuits every refresh, which is why auto-sync must never leave that state stuck.

## Automatic vault sync triggers

Every note write, app boot, and foreground return starts a **full vault sync**
(`AppSyncViewModel.requestAutoSync()`), not merely a status refresh: inbox note create, note editor
save, inbox delete, boot, and every real foreground `ON_START`. `requestAutoSync()` is the
sole entry point for automatic triggers and runs the same code path as the manual button.

Boot's trigger waits for `launchSettled` plus one rendered frame and fires once per
`AppSyncViewModel` instance after settle (`shouldTriggerBootSync`). The flag is keyed to that
instance: a composition-lifetime flag would leave a ViewModel recreated by branch/remote changes
stuck in `SyncUiState.Loading`. The foreground observer ignores only the synthetic `ON_START` that
`LifecycleRegistry` may replay while `addObserver` runs (`shouldAutoSyncOnLifecycleEvent`); every
later resume syncs. Cold start is boot's job, so the two do not double-fire. Both live in
[AppBootEffects.kt](app/src/main/java/com/eskerra/go/app/AppBootEffects.kt).

Rules:

- **No remote configured** → do not sync, but still run a quiet **local** status refresh. A pure no-op
  would strand a local-only vault in `SyncUiState.Loading` forever, since these triggers are now the
  only thing that advances that state on boot.
- **Blocked preflight** (`!preflight.canSync`, e.g. unsafe local paths) → do **not** sync; run a quiet
  local status refresh so the shell badge still tells the truth. A blocked preflight is not an error
  state and is not retried.
- **Sync already in flight** → coalesce: mark one follow-up and run it when the current sync
  finishes. N requests during one sync collapse to exactly one follow-up, never a queue.
- **`SyncError.SyncAlreadyRunning`** (a podcast channel holds the shared git mutex) is **not a
  failure**: record no attempt, emit no error, and retry after a short delay. Retries are capped
  (`MAX_AUTO_SYNC_CONTENTION_RETRIES`); on exhaustion give up quietly and clear `SyncUiState.Syncing`
  so the shell unblocks. Leaving it uncapped would pin the shell on `Syncing` — which every status
  refresh early-returns on — and wedge auto-sync entirely if that channel ever hung.
- **Failures are silent.** An automatic sync that fails records the attempt and sets
  `SyncUiState.Error`, which surfaces only as the `"!"` shell badge plus detail on the sync screen.
  No toasts, no dialogs. Manual sync keeps its own messaging.

Feedback-loop safety: a successful sync calls `markInboxNotesChanged`, which the inbox route answers
with `refresh()`. `InboxViewModel.refresh()` must therefore never invoke `onInboxMutated` (which is a
write-site trigger), or writes and refreshes would loop. Pinned by
`InboxViewModelFeedbackLoopTest`.

## Out of scope

WorkManager/AlarmManager scheduled sync, SSH, interactive conflict-resolution UI, full sync history, note deletion/move/rename (inbox delete is implemented separately).

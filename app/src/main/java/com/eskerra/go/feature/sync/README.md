# feature/sync

## Purpose

Syncing, as the user meets it: the manual sync screen, the merged Sync settings
screen (remote/branch/token, vault R2 config, this-device name, downloaded
binaries), the shell's spinner rule, and the orchestration that decides *when*
an automatic sync runs. The slice runs no Git itself — the engine and the
process-wide mutex live in `data/git`, reached through `core/usecase`; see
[`specs/architecture/sync-hardening-and-recovery.md`](../../../../../../../../../specs/architecture/sync-hardening-and-recovery.md).

## Key files

- `AppSyncViewModel.kt` — app-scoped sync state: runs vault sync, feeds the
  shell indicator, and owns `requestAutoSync()`, the single entry point for
  every automatic trigger (boot, foreground return, note write).
- `BinariesViewModel.kt` — the "Downloaded binaries" tile: lists on-device
  binaries and runs their manual sync.
- `SyncSettingsViewModel.kt` — remote URI, branch, and replacement token;
  save / test-connection / clear.
- `VaultSettingsViewModel.kt` — vault-level R2 credentials plus the local-only
  display and device names; splits one form across two stores.
- `SyncScreen.kt` / `SyncSettingsScreen.kt` / `SyncSettingsSections.kt` /
  `BinariesTile.kt` — stateless screens and tiles; one `*UiState.kt` each.
- `SyncSpinnerVisibility.kt` — `holdTrueAtLeast`, the shell spinner's
  anti-flash operator.

## State owner

Each ViewModel owns its own `StateFlow` and nothing else's. Since Q1 batch 6
the *sync operation* state lives here too: `AppSyncViewModel` is the single
source of truth for sync progress and the shell's spinner, and `app/` keeps
only the composition-root wiring that constructs it. Do not introduce a second
holder of sync progress anywhere.

**Settings slice decision (2026-08-08):** there is deliberately **no
`feature/settings/`**. Settings today are sync-centric — remote, token, R2,
device name — so they live with the sync UI. Revisit only when parity P2
(settings-document adoption) gives settings real domain content of its own.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.sync.*"`

The AppSync tests carry the slice's fakes (`FakeRemoteSyncRepository.kt`, which
also declares `FakeRegistryRepository`); `app/`'s `ReconcileWorkspaceConfigTest`
imports the former. **`SyncSettingsViewModel` and `BinariesViewModel` have no
tests yet** — a known gap, tracked under Follow-ups in
`specs/plans/make-slices-real.md`; any change to either should arrive with one.

## What not to touch

- **`requestAutoSync()` is the sole entry point for automatic sync, and its
  coalescing is load-bearing.** A request arriving while a sync runs sets
  `pendingAutoSync` and is replayed once in the `finally` block — never queued
  twice, never dropped. The local-only branch (blank `remoteUri`) must keep
  refreshing status, or a vault with no remote is stuck in `Loading` forever
  with no retry affordance.
- **Losing the race for the shared git mutex is not an error.** After
  `MAX_AUTO_SYNC_CONTENTION_RETRIES` the code deliberately records nothing,
  shows no error, and just clears `Syncing` so the shell stops spinning; the
  next write or foreground return syncs again. Do not "fix" this into a
  user-visible failure.
- **Secrets must not leak into displayed state.** `RemoteSyncSettingsUiState`
  exposes `hasStoredCredential`, never the stored token — `replacementToken`
  is write-only input, and `saveSettings()` resets it to `""` from freshly
  reloaded settings on success. Do not "preserve the user's input" there.
- **`clearSettings()` clears remote config, not notes** — its message promises
  "Local notes are unchanged"; keep it that way.
- **`VaultSettingsViewModel.save()` writes two stores** (vault `EskerraSettings`
  via `saveVaultSettings`, device-local names via `saveLocalSettings`) and
  builds the shared object through `buildEskerraSettingsFromForm(previousShared
  = lastShared)` so untouched remote fields survive. Do not construct
  `EskerraSettings` inline — that silently drops fields written by another
  client.
- **`holdTrueAtLeast` delays only the `true` → `false` edge.** `false` passes
  through immediately. Inverting that would either flash the spinner on fast
  syncs or leave it stuck; the behavior is pinned by `SyncSpinnerVisibilityTest`.
- No Git from this slice — no JGit calls, no direct file writes. Every mutation
  goes through a use case, so the process-wide git mutex stays the only writer.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.

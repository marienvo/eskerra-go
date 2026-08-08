# feature/sync

## Purpose

Everything the user sees *about* syncing: the manual sync screen, the merged
Sync settings screen (remote/branch/token, vault R2 config, this-device name,
downloaded binaries), and the shell's spinner rule. The slice renders sync
state and collects settings — it runs no Git. The engine, the shared mutex,
and the auto-sync triggers live in `data/git` and `core/usecase`; see
[`specs/architecture/sync-hardening-and-recovery.md`](../../../../../../../../../specs/architecture/sync-hardening-and-recovery.md).

## Key files

- `SyncSettingsViewModel.kt` — remote URI, branch, and replacement token;
  save / test-connection / clear.
- `VaultSettingsViewModel.kt` — vault-level R2 credentials plus the local-only
  display and device names; splits one form across two stores.
- `SyncScreen.kt` / `SyncSettingsScreen.kt` / `SyncSettingsSections.kt` /
  `BinariesTile.kt` — stateless screens and tiles; one `*UiState.kt` each.
- `SyncSpinnerVisibility.kt` — `holdTrueAtLeast`, the shell spinner's
  anti-flash operator.

## State owner

Each ViewModel owns its own `StateFlow` and nothing else's. The *sync
operation* state is **not** owned here: `AppSyncViewModel` (still in `app/`,
moves in Q1 batch 6) drives sync and consumes this slice's `holdTrueAtLeast`.
Do not add a second source of truth for sync progress in this slice.

**Settings slice decision (2026-08-08):** there is deliberately **no
`feature/settings/`**. Settings today are sync-centric — remote, token, R2,
device name — so they live with the sync UI. Revisit only when parity P2
(settings-document adoption) gives settings real domain content of its own.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.sync.*"`

`VaultSettingsViewModelTest` uses fake stores and a `StandardTestDispatcher` —
no filesystem, no network. **`SyncSettingsViewModel` has no test yet** (a
known gap, tracked in `specs/plans/make-slices-real.md` §Q6): any change to it
should arrive with one.

## What not to touch

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

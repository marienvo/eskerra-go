# feature/inbox

## Purpose

The inbox list: show the capture notes of the active Today Hub, let the user
select entries and delete them. This slice owns the inbox screen and its
ViewModel; it does not own inbox scanning, note writing, or git sync.

## Key files

- `InboxScreen.kt` — composable screen; renders state, forwards selection and
  delete intents via callbacks.
- `InboxUiState.kt` — sealed UI state (`Loading`, `Content`, `Empty`, `Error`).
- `InboxViewModel.kt` — owns `uiState`/`selectedNoteIds`/`isDeleting`/
  `deleteError` as `StateFlow`; talks to `LoadInboxSummariesCached` and
  `DeleteInboxNotes` (`core/usecase`) and reads the active hub from
  `ActiveTodayHubStore`. No direct file I/O or JGit calls — those live in
  `data/notes` and `data/git` per the hybrid-layering ADR.

## State owner

`InboxViewModel` is the single source of truth for the visible inbox list and
the selection. It keeps the full cross-hub summary list privately and filters
it to the active hub folder; the screen is stateless and never derives or
caches list or selection state itself.

Deletes are single-flight (`isDeleting` gates re-entry) and only ever target
ids that are present in the current `Content` state.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.inbox.*"`

`InboxViewModelTest.kt` covers load/refresh, hub filtering, selection and
delete-error mapping; `InboxViewModelRegistryObservationTest.kt` covers
external registry updates; `InboxViewModelFeedbackLoopTest.kt` guards against
refresh loops. All use fakes — no Android framework, filesystem, or git.

## What not to touch

- The inbox↔sync boundary. Sync and every other writer refresh the shared
  `NoteRegistryCache` (`data/notes`); this slice only *observes*
  `LoadInboxSummariesCached.registryUpdates` so remote deletes drop out of the
  list. Do not make the ViewModel write to the registry, and do not remove
  that collector — it is the backstop when the Compose `inboxRefreshSignal`
  path is missed.
- Delete semantics and the remote-delete path (`DeleteInboxNotes`, registry
  refresh, commit/push) — shared infrastructure on the vault-write surface.
- Hub-scoped inbox rules (`InboxNotePath`, `TodayHubDiscovery` in `core/`) —
  this slice filters by hub folder, it does not define the layout.
- `AppInboxRoute.kt` in `app/` — app-level wiring (ViewModel construction,
  `inboxViewModelKey()`, refresh-signal plumbing); it intentionally stays in
  `app/`, not this slice.

See [`specs/architecture/sync-hardening-and-recovery.md`](../../../../../../../../../specs/architecture/sync-hardening-and-recovery.md)
for the sync/write rules this slice must not restate or bypass, and
[`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules.

# feature/note

## Purpose

Reading a single Markdown note: render the resolved document, expose whether
it may be edited, and warm linked notes in the background. Read-only by
design — every write path belongs to `feature/editor`.

## Key files

- `NoteScreen.kt` — composable screen; renders the document and the edit
  affordance when `canEdit` is true.
- `NoteReaderUiState.kt` — sealed UI state (`Loading`, `Content`,
  `InvalidNoteId`, `NotFound`, `Error`).
- `NoteReaderViewModel.kt` — owns the reader `StateFlow`; calls
  `LoadNoteForReading` and the optional `PrefetchLinkedNotes`. No direct file
  I/O or JGit calls — those live in `data/notes` and `data/git`.

## State owner

`NoteReaderViewModel` is the single source of truth for the open document and
its error state. Two jobs are tracked explicitly — `loadJob` and
`prefetchJob` — and both are cancelled before a reload, so a fast note switch
cannot let a stale prefetch or a late `load` overwrite the newer document.
Keep that cancel-then-launch order if you touch `load`.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.note.*"`

`NoteReaderViewModelTest.kt` covers load success, every failure mapping, and
retry with fake use cases — no Android framework or real filesystem/git access
needed. Prefetch scheduling/cancellation is **not** covered yet; add a test
there before changing `schedulePrefetch`.

## What not to touch

- **`canEdit = document.note.isInbox`** — the reader only reports the
  read-only rule; it does not define it. The rule is a product contract
  (`specs/architecture/app-contract.md`) and changing it is parity work
  (P1a), not a slice edit.
- `PrefetchLinkedNotes` and wiki-link resolution — shared `core`/`data`
  infrastructure; the reader may schedule prefetch, never re-implement
  resolution or caching.
- `app/NoteReaderNavSignals.kt` — cross-screen saved-state flags
  (`notesChanged`, `noteContentChanged`) and external link opening. It needs
  `Context`/`NavHostController`, so it intentionally stays in `app/`, not this
  slice.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.

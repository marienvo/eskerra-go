# feature/editor

## Purpose

Editing and creating Markdown notes: the editor screen for an existing note,
and the inline "new note" composer that writes into the Today Hub inbox. This
slice owns the draft-versus-persisted distinction; it does not own the write
itself — that lives in `core/usecase` + `data/notes`.

## Key files

- `NoteEditorScreen.kt` — composable screen; renders state, forwards edits and
  save via callbacks.
- `NoteEditorUiState.kt` — sealed UI state (`Loading`, `Content`,
  `InvalidNoteId`, `NotFound`, `Error`) plus `CreateInboxUiState`.
- `NoteEditorViewModel.kt` — owns the editor's `StateFlow` and `noteSavedEvents`
  channel; calls `LoadEditableNote`, `SaveNote`, `LoadGitStatusSummary`. The
  same file also declares **`CreateInboxNoteViewModel`** (new-inbox-note
  composer, driven from `app/ShellNewNoteInputState.kt`) — two ViewModels, one
  file, by history.

## State owner

`NoteEditorViewModel` is the single source of truth for the draft buffer,
dirty flag, and save-in-flight state; `CreateInboxNoteViewModel` likewise for
the shell's new-note input. Both screens are stateless — no composable caches
or re-derives draft text, and a save in flight is rejected at the ViewModel
(`isSaving` guard), never by disabling a button alone.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.editor.*"`

`NoteEditorViewModelTest.kt` (load, save, dirty flag, saved-event emission,
read-only state), `NoteEditorErrorMappingTest.kt` (the read-only rejection and
registry-refresh failure messages), `CreateInboxNoteViewModelTest.kt`. All use
fake use cases — no real filesystem or git.

## What not to touch

- **`persistedMarkdownToDraft` / `draftMarkdownToPersisted`** — the inbox-only
  round trip through `InboxNoteDraft`. These two must stay exact inverses: any
  asymmetry silently rewrites user Markdown on save. Changing them is a
  markdown-integrity change (G3), never a drive-by edit.
- **`canEdit`** — the non-inbox read-only rule is a product contract
  (`specs/architecture/app-contract.md`), not a UI detail. Flipping it belongs
  to parity P1a, with the contract doc in the same PR.
- `SaveNote` / `LoadEditableNote` and their `data/notes` implementations —
  shared vault-write infrastructure, not slice-owned.
- `app/ShellNewNoteInputState.kt` — app-level wiring that hosts
  `CreateInboxNoteViewModel` outside this slice's screen; it stays in `app/`.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.

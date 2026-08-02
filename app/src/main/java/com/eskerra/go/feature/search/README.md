# feature/search

## Purpose

Full-vault search: query the Markdown vault (titles and, once the body index
is ready, note bodies) and show ranked results. This slice owns the search
screen and its ViewModel; it does not own indexing infrastructure.

## Key files

- `SearchScreen.kt` — composable screen; renders state, forwards user input
  via callbacks.
- `SearchResultRow.kt` — single result row composable.
- `SearchUiState.kt` — sealed UI state (`Idle`, `Opening`, `Searching`,
  `Results`, `NoMatches`, `Error`).
- `SearchViewModel.kt` — owns `query`/`SearchUiState` as `StateFlow`; talks to
  `SearchVault`, `MaintainVaultSearchIndex`, `RepairVaultSearchIndex` (all
  `core/usecase`). No direct file I/O or JGit calls — those live in
  `data/notes` and `data/git` per the hybrid-layering ADR.

## State owner

`SearchViewModel` is the single source of truth for query text and result
state. The screen is stateless: it reads `StateFlow`s and calls ViewModel
methods, it never derives or caches search state itself.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.search.*"`

`SearchViewModelTest.kt` covers query debouncing, index-not-ready states, and
error/retry paths with fake use cases — no Android framework or real
filesystem/git access needed.

## What not to touch

- Search-index maintenance/repair logic (`MaintainVaultSearchIndex`,
  `RepairVaultSearchIndex`, and their `data/` implementations) — shared
  infrastructure, not slice-owned.
- `AppVaultSearchSideEffects.kt` / `AppSearchIndexEffects.kt` in `app/` —
  app-level wiring that triggers indexing outside the search screen's
  lifecycle; they intentionally stay in `app/`, not this slice.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.

# feature/todayhub

## Purpose

The Today Hub (spec §11): pick a `Today.md` hub, show its intro, and page
through one week of rows at a time. The slice orchestrates and renders only —
week math, stem parsing, and navigation windows live in `core/todayhub`, and
every read goes through `LoadTodayHub` / `LoadTodayHubRow`.

## Key files

- `TodayHubViewModel.kt` — owns the state flow, the selected week, and the
  active-hub persistence.
- `TodayHubUiState.kt` — `Loading` / `Empty` / `Error` / `Content`; `Empty`
  means "index ready, no `Today.md` anywhere", not "not loaded yet".
- `TodayHubScreen.kt` / `TodayHubPickerSheet.kt` — header, body, week nav bar,
  hub columns; the picker shows only when `showHubPicker`.
- `TodayHubWeekProgressStrip.kt` — the date-column bar; segment `widthPx` is
  consumed as a layout **weight**, so the merged weekend is twice as wide.

## State owner

`TodayHubViewModel` is the single source of truth. Two jobs are tracked —
`loadJob` and `rowJob` — and both are cancelled before a reload, so a fast hub
switch cannot let a late row overwrite the newer one. Every row result is
additionally guarded by `if (loaded?.selectedStem == stem)`: keep both the
cancel and the stem check, they cover different races.

## Tests to run

`./scripts/gradle.sh :app:testDebugUnitTest --tests "com.eskerra.go.feature.todayhub.*"`

`TodayHubViewModelTest.kt` injects `today: () -> LocalDate` and fake stores, so
week behavior is deterministic with no Android framework, clock, or real
filesystem. Any change to week selection needs a test at a pinned date.

## What not to touch

- **The boot path.** `restoreSnapshot()` paints from the persisted snapshot
  *before* the real load, and `load()` deliberately does not fall back to
  `Loading` once `Content` is on screen — that is what stops the hub flashing
  empty on cold start (`specs/architecture/boot-optimization.md`). Do not
  "simplify" the snapshot-first branch or the retained-row logic in
  `onHubLoaded`.
- **`saveSnapshotIfCurrentWeek()`** — the snapshot must only ever hold the
  current week; persisting a browsed past week would restore the user into the
  wrong week on next launch.
- **The 200 ms `ROW_NAV_LOADING_DELAY_MS` spinner** — the delay exists so fast
  week steps never flash a spinner (spec §11.4). Removing the delay is a
  product change, not a cleanup.
- `resetToCurrentWeek()`'s `Boolean` return is a contract: `app/AppInboxRoute.kt`
  scrolls to top only when it returns `true`.
- Week math (`TodayHubWeeks`, `TodayHubNavigation`, `TodayHubLabels`) is shared
  `core` — extend it there, never re-implement stem or progress math here.

See [`specs/adr/001-hybrid-layering-and-feature-slices.md`](../../../../../../../../../specs/adr/001-hybrid-layering-and-feature-slices.md)
for the placement rules this slice follows.

# Snappy boot & navigation — follow-up plan

Follow-up to [snappy-boot-and-navigation-plan.md](snappy-boot-and-navigation-plan.md)
(removed from git) and [boot-optimization.md](../architecture/boot-optimization.md).

Two regressions/leftovers remain after the first pass:

1. A **double spinner** appears right after the splash screen, then collapses to a
   single spinner for a moment.
2. **Page-to-page navigation has an unnecessary fade** that even flickers.

Goal stays the same: assume nothing changed, show the *last known state*
instantly, and only swap in changes once they are actually ready. Transitions
must feel instant — no fade.

## Problem analysis

### 1. Double spinner after splash

Two independent spinners overlap on cold start:

- **Today Hub spinner (the persistent one).** Unlike the inbox, the Today Hub has
  **no persisted snapshot cache**. `TodayHubViewModel` always starts in
  `TodayHubUiState.Loading` ([TodayHubViewModel.kt:45](../../app/src/main/java/com/eskerra/go/app/TodayHubViewModel.kt)),
  which renders the `"Loading vault…"` spinner
  ([TodayHubScreen.kt:56](../../app/src/main/java/com/eskerra/go/feature/todayhub/TodayHubScreen.kt)).
  The splash is dismissed as soon as the *inbox* is settled — `isLaunchSettled`
  only inspects the gate and `InboxUiState`, never the Today Hub
  ([AppLaunchSettled.kt:12](../../app/src/main/java/com/eskerra/go/app/AppLaunchSettled.kt)).
  So the moment the splash leaves, the Today Hub is still `Loading` and its
  spinner is on screen. This is the "1 die nog even in beeld blijft".

- **Inbox refresh indicator (the second one).** With an inbox snapshot present,
  the inbox renders `Content(isRefreshing = true)` and revalidates in the
  background. If revalidation outlives the 300 ms debounce, the small
  `CircularProgressIndicator` at [InboxScreen.kt:152](../../app/src/main/java/com/eskerra/go/feature/inbox/InboxScreen.kt)
  appears *on top of* the Today Hub spinner → two spinners at once. When the
  inbox finishes, this one disappears and only the Today Hub spinner remains.

Net effect: two spinners → one spinner → content. Exactly the reported behavior.

### 2. Fade on navigation

`NavHost` in [App.kt:203](../../app/src/main/java/com/eskerra/go/app/App.kt) sets no
transition parameters, so navigation-compose applies its **default fade-through**
enter/exit animations. Because each destination owns its own background, the
crossfade briefly shows both layers / the scrim, which reads as a flicker. The
fade adds nothing here; navigation should be instant.

## Changes

### Change A — Today Hub stale-while-revalidate snapshot

Mirror the inbox SWR pattern so the Today Hub shows its last rendered state
instantly and never shows a full-screen spinner on cold start.

- Add a `FileTodayHubSnapshotStore` (under `data/todayhub/`) keyed by
  `GateFingerprintComputer.compute(config, filesDir)`, written to
  `filesDir/cache/today_hub_snapshot.json`. Model it directly on
  [FileInboxSnapshotStore.kt](../../app/src/main/java/com/eskerra/go/data/notes/FileInboxSnapshotStore.kt)
  + its codec (no `org.json`, JVM-test-friendly).
- Snapshot payload = the minimum needed to re-render `TodayHubUiState.Content`
  for the **current week** without any reads: `activeHubId`, `folderLabel`,
  `weekRangeLabel`, nav flags, `introMarkdown`, the resolved row columns, and the
  `progressSegments`. Persist the registry reference lazily (the registry already
  has its own persisted snapshot from the first pass, so the snapshot can store
  ids and rehydrate the registry from `NoteRegistryCache`).
- `TodayHubViewModel.init`: read the snapshot first; if present, emit
  `Content(rowLoading = false)` immediately (stale), then run the existing
  `load(...)` in the background. Only replace state when the freshly computed
  Content differs (reuse the inbox equality-diff idea so an unchanged hub causes
  no reflow).
- Write the snapshot whenever a fresh `Content` for the current week is produced
  (`onHubLoaded` / `loadRow` success). Invalidate on the same events that
  invalidate the registry (save / create / delete / successful manual sync) so a
  changed vault does not show stale hub data indefinitely.

Result: post-splash the Today Hub shows real (stale) content, not a spinner.

### Change B — Remove the refresh indicator entirely

**Decision: drop the refresh indicator completely.** Revalidation is always
silent; content swaps in without any spinner, on boot and afterwards.

- Remove the `showRefreshIndicator` state, its debounced arming, and the
  `CircularProgressIndicator` block at
  [InboxScreen.kt:152](../../app/src/main/java/com/eskerra/go/feature/inbox/InboxScreen.kt).
  Simplify `refresh(showFullScreenLoading)` in
  [InboxViewModel.kt:106](../../app/src/main/java/com/eskerra/go/app/InboxViewModel.kt)
  accordingly (no `indicatorJob`, no `REFRESH_INDICATOR_DELAY_MS`). The
  full-screen `Loading` state stays for the genuine no-cache first load only.
- Drop the `showRefreshIndicator` plumbing through `InboxScreen` /
  `AppInboxRoute` and the related `InboxViewModelTest` cases (replace with
  "revalidation never shows a spinner").
- The Today Hub background revalidation from Change A is silent by the same rule
  (no `rowLoading` spinner on cold-start revalidate; swap on diff).

With A + B there is **no spinner at all** on a warm cold start — last state shows
immediately, changes appear when ready.

> Note: `isLaunchSettled` is intentionally left as-is (gate + inbox only). The
> Today Hub snapshot from Change A means the hub is never `Loading` post-splash on
> a warm start. The first-ever launch (no snapshot yet) may briefly show the
> Today Hub's full `Loading` state — accepted; not worth holding the splash for.

### Change D — Instant navigation, no fade

Disable navigation animations on the `NavHost` in
[App.kt:203](../../app/src/main/java/com/eskerra/go/app/App.kt):

```kotlin
NavHost(
    navController = navController,
    startDestination = AppRoute.INBOX,
    modifier = contentModifier,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None }
)
```

This removes the crossfade/flicker and makes destination swaps instant. If a
single screen later wants its own transition it can still override per
`composable`.

## Verification

- `TodayHubViewModelTest`: cold start with a snapshot emits `Content` first (never
  `Loading`); background revalidation with unchanged data does not reflow; changed
  data swaps in.
- New `FileTodayHubSnapshotStoreTest` + codec test (round-trip, fingerprint
  mismatch ignored, empty/!exists → null), mirroring
  `FileInboxSnapshotStoreTest`.
- `InboxViewModelTest`: revalidation never surfaces a spinner (replace the old
  `showRefreshIndicator` cases); full-screen `Loading` only on a true no-cache
  first load.
- Manual: cold start shows no spinner with a warm cache; navigation between tabs
  and into/out of a note shows no fade and no flicker.
- Quality gate: `ktlintCheck`, `lintDebug`, `testDebugUnitTest`, module budgets.

## Out of scope

- Reworking the registry / content caches (done in the first pass).
- Background sync, multi-workspace snapshot keys.
- Per-screen bespoke transitions (navigation is simply instant for now).

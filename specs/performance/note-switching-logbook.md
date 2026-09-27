# Note-switching performance logbook

Measurement trail for making note-to-note navigation (back, and tapping a link to another note)
feel instant, without touching the startup path. The durable destination for anything that becomes
an invariant is [`specs/architecture/boot-optimization.md`](../architecture/boot-optimization.md)
(prefetch / cache section); this file is the evidence and the day-to-day notes.

Metric definitions:

- **back-to-home** — wall-clock time from tapping the system/shell back button on a note to the
  previous note (or home) being fully painted (title + body together).
- **link-tap** — wall-clock time from tapping a `[[wikilink]]` or inline note link to the target
  note being fully painted.
- **title-before-body gap** — whether the title heading is visible for one or more frames before
  the body renders (a symptom of the async parse race this work removes).

---

## 2026-09-27 — PR 1: atomic title+body publish, cache sizing, registry-refresh throttle

**Problem observed (user report).** Clicking back through notes is slow; a link tap always has a
delay comparable to back; the title often renders visibly before the body. Inbox note taps stay
fast (small notes). Cold start must not regress.

**Root causes found (static analysis, see PR description for full write-up):**
1. `NoteReaderUiState.Content` was published with only the raw `bodyMarkdown`; `VaultMarkdownView`
   parsed it asynchronously afterwards (`LaunchedEffect` + `ParsedMarkdownCache.get`). This is why
   the title (a plain `Text`) always painted before the body — the parse is a separate, later step
   by construction, not a corner case.
2. `ParsedMarkdownCache`'s default size (16 entries) was shared by the reader, prefetch, and every
   Today Hub cell. A single densely-linked note's prefetch pass could evict every back-stack note's
   parsed body, turning "back" into a full re-parse — and because the empty-until-parsed body
   collapsed the scrollable content's height, the restored scroll offset then clamped to 0.
3. No in-flight de-duplication: a prefetch parsing note X and a tap landing on X moments later
   would each parse independently, wasting CPU exactly when the user is waiting.
4. `LoadNoteForReading` dispatched a full incremental vault-registry refresh on **every** note open,
   competing for IO/CPU with the note actually being loaded — redundant, since sync, foreground
   return, and writes already keep the registry current.

**Changes.**
- `NoteReaderViewModel` now calls `ParsedMarkdownCachePort.get(bodyMarkdown)` *before* publishing
  `Content`; `NoteReaderUiState.Content` carries the resulting `preparedBody`. On a warm cache hit
  this does not suspend, so title and body always land in the same state update, hence the same
  frame. `VaultMarkdownView` gained a `preparedOverride` parameter to render it directly, skipping
  its own cache lookup for this call site (Today Hub cells are unaffected, still go through the
  cache lookup path).
- `ParsedMarkdownCache.DEFAULT_SIZE` and `NoteContentCache.DEFAULT_SIZE`: 16 → 64. Entries are
  parsed-AST / string handles, not raw file bytes — cheap to keep around at this scale.
- `ParsedMarkdownCache.get` de-duplicates concurrent misses for the same body onto one in-flight
  parse (`CompletableDeferred` keyed by body text under a `Mutex`).
- `LoadNoteForReading`'s background registry refresh is throttled to at most once per 30s
  (injectable clock); the cold-miss path is untouched (it has nothing to throttle against yet).

**Expected effect.** Back navigation: previously-parsed notes stay in cache long enough to survive
a normal prefetch pass, and even on a genuine miss the body is fully ready before the note repaints
— so scroll position is never clamped to 0. Every note open: no title-before-body gap on a warm
hit. Inbox taps: unaffected (already fast; now also benefit from in-flight dedup and are on the
same atomic-publish path).

**Verification done.** Full unit suite green (`./gradlew :app:testDebugUnitTest`), ktlint clean,
module budgets unaffected, ArchUnit frozen-violation store refreshed for the two composables'
changed signatures (`NoteReaderContent`, `VaultMarkdownView`) and `NoteReaderViewModel`'s
constructor — same pre-existing `File`-parameter violations, just under new mangled names.

**Not yet done (needs a device).** On-device before/after timing for back-to-home and link-tap,
and a `ColdStartTrace`-style capture to confirm launch-settled timing is unchanged. Whoever picks
up a device for this should log `adb logcat` timestamps for note-open → first frame across a
walk (home → hub link → 3–4 links deep → back to home) and fill in the numbers here.

---

## 2026-09-27 — PR 2: viewport-aware prefetch scheduler + "wait briefly, then switch" on tap

**Problem observed (user report, continued).** A link tap always has delay comparable to "back".
The user asked that links currently in view be preloaded so a tap has the best chance of an
instant switch — prioritized by what's actually on screen, not just document order.

**What PR 1 didn't fix.** `PrefetchLinkedNotes` ran inside each note's own `viewModelScope`, in
document order, with no priority signal and no relationship to what was on screen. Because reader
ViewModels stay alive on the back stack, forward navigation never cancelled a prior note's prefetch
job, so several notes' prefetch batches could end up running (and competing for CPU) at once. A tap
itself never waited for anything — it always painted `Loading` first on a miss, even a near-miss.

**Changes.**
- `PrefetchLinkTargets` gained `resolveWithOffsets` (candidates + the character offset of their
  first link occurrence) and a pure `orderByViewport(targets, markdownLength, visibleStartFraction,
  visibleEndFraction)` that ranks a target inside the visible window first, then by distance to it.
- New `WarmNote` use case: the single "load content, warm parsed body" step, extracted so the
  background scheduler and a direct tap warm exactly the same way (and so a tap landing on an
  already-warming target does no redundant work — the caches themselves de-duplicate).
- New `NotePrefetchScheduler`: one process-wide instance (wired once in `MainActivity`, not
  per-note), concurrency 2. Every `submit` cancels whatever batch was previously running and starts
  fresh — "the newest note always wins" — so switching notes, or scrolling to a different part of
  one, immediately reprioritizes background work instead of piling it up. Replaces
  `PrefetchLinkedNotes` (removed).
- `NoteReaderViewModel` submits its open note's links ordered by `orderByViewport` and resubmits as
  `NoteScreen` reports the top-of-viewport scroll fraction (debounced ~150ms via `snapshotFlow`).
- New `NoteOpenGate.openNoteWithWarmBudget`: every note-opening tap in the app (a link inside a
  note, an ambiguous-link pick, an inbox tile, a Today Hub cell link, a search result) now waits up
  to 150ms for `WarmNote` on the target before navigating. An already-warm target (from prefetch or
  a prior visit) returns near-instantly, so the budget is only ever spent on a genuine miss, and a
  true miss still navigates once it elapses — the reader's `Loading` state covers the remainder.
  Inbox notes are small enough that this budget is essentially never felt.

**Not yet done.** Home-screen (Today Hub) prefetch after launch has settled — planned as PR 3, not
built yet. On-device confirmation that link taps feel instant and that the 150ms budget never
causes a *perceptible* stall on a genuine cold miss (it shouldn't — `Loading` already painted
instantly before this change too, just with no chance of an atomic swap).

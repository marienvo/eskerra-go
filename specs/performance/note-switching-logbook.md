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

**Not yet done.** On-device confirmation that link taps feel instant and that the 150ms budget
never causes a *perceptible* stall on a genuine cold miss (it shouldn't — `Loading` already painted
instantly before this change too, just with no chance of an atomic swap).

---

## 2026-09-27 — PR 3: home-screen (Today Hub) prefetch after launch settled

**Problem observed (user report, continued).** The first link tap from the home screen was always
cold — no prefetch ran there at all before this change.

**Change.** `AppBootEffects` now submits the Today Hub's currently visible links (intro markdown +
the loaded week row's columns, via the new pure `TodayHubPrefetchTargets.resolve`) to the shared
`NotePrefetchScheduler`, gated by the new `shouldSubmitHomePrefetch` predicate: only once
`launchSettled` is true and there is Today Hub content to read links from. The submit itself waits
one frame past settle plus a fixed `HOME_PREFETCH_DELAY_MS` (500ms), so it never contends with the
settling frame or the boot-sync kickoff that also gates on `launchSettled`. It resubmits whenever
the Today Hub content changes (week navigation, hub switch).

Because `NotePrefetchScheduler.submit` always cancels and replaces whatever batch was running,
this home batch is automatically the lowest priority in practice: the moment a note is opened (from
home or anywhere else), that note's own submit supersedes it — no separate priority mechanism was
needed.

**Verification done.** Full unit suite green, ktlint clean, module budgets unaffected, no ArchUnit
signature drift this time. `shouldSubmitHomePrefetch` is unit-tested the same way the file's other
launch-settled gates already are (pure predicate, no Compose test harness needed).

**Not yet done (needs a device).** Confirming cold-start / launch-settled timing is unaffected by
this addition (it should be, by construction — it's strictly a post-settle side effect — but only a
device run with `ColdStartTrace` / `scripts/measure-cold-start.sh` confirms it).

---

## 2026-09-27 — Follow-up: home links still wait, even after 10s in view

**User report.** On Home, 3 links to long (50+ item) list notes sit in the viewport for ~10s —
long enough that background prefetch should have finished — yet a tap still waits, now
*specifically before the page appears* (a direct effect of PR 1's atomic-publish fix: previously
the title rendered first while the body was still loading, masking this same underlying cost).
Tested on a **debug** build (2–5x slower Compose rendering than an optimized build).

**Root-cause analysis (see the plan for the full write-up); three suspects identified:**
1. Nothing was actually measured before now — PR 1's step 0 (a `NoteOpenTrace`) was planned but
   never built. Fixed in the commit right before this one (`NoteNavTrace`).
2. `NoteScreen` renders a note's entire body in one non-lazy `Column(verticalScroll)`. Prefetch
   only ever warmed *data* (file content + parsed AST) — it cannot shrink first-frame composition
   and layout cost, which for a 50+ item list is substantial and lands entirely before the page now
   appears (this commit does not yet fix this; see the plan's Step 2).
3. **Prefetch resolved links differently than a tap did.** Prefetch used a regex-based scan
   (`WikiLinkParser` + a hand-rolled `[label](href)` matcher); a tap resolves through the *parsed*
   AST via `VaultReadonlyLink.targetFor`. An href the AST parser accepts but the regex scan didn't
   handle (angle-bracket-wrapped destinations, a trailing `"title"`, or the AST's own idea of where
   a link's boundaries are) meant prefetch could silently skip a link that was, in fact, tappable —
   exactly the symptom reported.

**Change (this commit fixes cause 3; cause 2 is next).**
- New `PreparedMarkdownLinks.resolve(prepared, sourceNoteId, registry)`: walks a `PreparedMarkdown`'s
  parsed AST (all segments, including callout bodies) and resolves every `INLINE_LINK` destination
  with `VaultReadonlyLink.targetFor` — the exact function a tap uses. One source of truth for "is
  this link tappable," used by both prefetch and the tap path.
- `PrefetchLinkTargets` shrinks to just `Target` and `orderByViewport` (the regex-based
  `resolve`/`resolveWithOffsets`/`extractInlineHrefs` are removed — superseded, not kept alongside).
- `NoteReaderViewModel` now resolves prefetch targets from `preparedBody` (already computed for the
  atomic publish) instead of re-scanning the raw markdown.
- `TodayHubPrefetchTargets` now parses the intro/column markdown through the same
  `ParsedMarkdownCache` `VaultMarkdownView` already populated (a cache hit, not a re-parse) and
  resolves through `PreparedMarkdownLinks` too, with the same blank-intro/blank-column guards the
  renderer itself uses.

**Verification done.** Full unit suite green (including a new `PreparedMarkdownLinksTest` covering
angle-bracket destinations, a title suffix, callout bodies, ambiguous/external/unresolvable links,
dedup, and first-seen order — cases the old regex scan handled inconsistently or not at all).
ktlint clean, module budgets unaffected, no ArchUnit drift.

**Not yet done.** Step 2 (lazy note rendering) is the change expected to actually close the
remaining gap the user is seeing — the AST-parity fix here corrects *which* links prefetch, not
*how expensive* the first frame is. On-device confirmation via `adb logcat -s NoteNav` that these 3
specific home links now show up in `prefetch.submit`/`warm.done` before the tap (they may already
have before this fix, if the regex scan happened to handle their exact href shape — the trace will
show either way).

---

## 2026-09-27 — PR 6: lazy rendering for long note bodies

**Problem observed (device trace).** The prefetch/cache work was doing its job for a home-link tap
on a 50+ item list note: every relevant cache and prefetch step was warm, and
`reader.published` (title and parsed body together) arrived at +4ms. Yet
`reader.firstFrame` did not arrive until +3054ms. The whole remaining wait was Compose composing
and laying out a body that `NoteScreen` put in one non-lazy `Column`.

**Change.** `NoteReaderContent` is now a single `LazyColumn`, retaining the existing UX where
title, path, Edit button, and body scroll together. `LazyNoteBlocks.split` splits a prepared
markdown run into one segment per top-level block, and chunks only an unordered list larger than
10 items. Each segment still goes through the same high-level `Markdown(state, colors,
typography, annotator, components, modifier)` renderer as before; it does not hand-roll the
library's per-node rendering or its CompositionLocals. Ordered lists remain whole because their
numbering can depend on their position. Segment keys combine index and source offset, while the
viewport prefetch signal now uses the visible lazy body items rather than scroll pixels.

**Verification done.** `LazyNoteBlocksTest` covers top-level splitting, unordered-list chunking,
order/text preservation, ordered-list pass-through, callouts, blank lines/link definitions, and
reference-style links. The full JVM quality gate is green:
`./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`; module budgets and
`git diff --check` are also clean. The debug APK was installed on the connected device.

**Not yet done (must happen before merge).** The installed device was locked, so no visual or
interaction verification could be performed. Unlock it, then test the original long-list note for
complete/correct bullets, spacing, images/tables/code blocks, smooth scrolling, and preserved
scroll position after back navigation. Re-run home → wait 10s → tap the list note with
`adb logcat -s NoteNav:I` and confirm that `reader.firstFrame - reader.published` is no longer a
multi-second gap. The `LIST_ITEM_CHUNK_SIZE = 10` is intentionally un-tuned until that measurement.

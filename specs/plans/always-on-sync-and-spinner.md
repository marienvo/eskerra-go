# Always-on sync + sync spinner

Last reviewed: 2026-08-02 (Phase A landed; B/C still open).
Status: **Phase A done** (spinner + hold + shell wiring; quality gate green). Phases B–C remain.
Track: **product** (parity-adjacent). This plan **absorbs parity phase P1b**
("foreground-resume sync trigger or prompt") from [`studio-feature-parity.md`](studio-feature-parity.md)
and goes further: it also makes every vault write and every cold boot a sync moment.

Companion docs (read before executing any phase):
[`specs/architecture/sync-hardening-and-recovery.md`](../architecture/sync-hardening-and-recovery.md),
[`specs/architecture/boot-optimization.md`](../architecture/boot-optimization.md),
[`specs/architecture/app-contract.md`](../architecture/app-contract.md),
[`specs/rules/change-safety.md`](../rules/change-safety.md),
[`specs/adr/001-hybrid-layering-and-feature-slices.md`](../adr/001-hybrid-layering-and-feature-slices.md).

**Hold (unchanged from the parity plan):** this plan and `make-slices-real.md` Q3's G3 sync inversions
touch the same sync use cases and may not share a review window. Whichever runs second waits.

## 1. Goal

Three product changes, in this order:

- **A — Sync spinner.** While a vault sync is running, the hamburger's badge slot shows a rotating
  two-arrow sync glyph *instead of* the pending-change count / attention dot, in exactly the same
  16×16 dp footprint, rotating around its own geometric center with zero wobble.
- **B — Writes always sync.** Creating an inbox note, saving a note edit, and deleting inbox notes
  each start a full vault sync (not just the local status refresh they do today).
- **C — Boot and focus always sync.** A full vault sync runs once after launch settles, and again on
  every return to the foreground — remote changes we do not know about yet are the point.

**Failure policy (decided 2026-08-02, product owner):** automatic sync fails **silently**. The shell
badge shows `"!"`, details live on the Sync screen; no toasts, no dialogs, no interruption.
Manual sync (menu → Sync) keeps today's messaging unchanged.

## 2. Verified current state (code facts, 2026-08-02)

| Fact | Where |
|---|---|
| Badge is a Material3 `Badge` inside a `BadgedBox` anchored to the hamburger `ShellChromeButton` | `app/AppShell.kt:93-104` |
| Badge text / count derived from `SyncUiState`; `Syncing` currently still renders the pre-sync count | `app/ShellSyncIndicatorMapping.kt:10-49`, `app/ShellSyncIndicatorState.kt:7` |
| `AppSyncViewModel.syncNow()` returns early when already `Syncing` (no queue, no coalescing) | `app/AppSyncViewModel.kt:137-141` |
| Menu sync is gated on `preflight.canSync`, else it opens the Sync screen | `app/AppNavigation.kt:128-148` |
| Inbox note create → `refreshLocalStatusQuietly()` only | `app/ShellNewNoteInputState.kt:62` |
| Note editor save → `refreshLocalStatusQuietly()` only | `app/AppNavGraph.kt:343-345` |
| Inbox delete → `onInboxMutated` → `refreshLocalStatusQuietly()` only | `app/AppInboxRoute.kt:66-75`, `app/InboxViewModel.kt:119-123` |
| Boot → `refreshShellStatusQuietly(forceRemote = true)` (read-only fetch, no commit/push) | `app/AppBootEffects.kt:22-24` |
| Foreground `ON_START` → `refreshShellStatusQuietly(forceRemote = false)`, 30 s debounce | `app/AppBootEffects.kt:39-52`, `AppSyncViewModel.kt:223` |
| `ManualSyncNow` already holds the shared `GitSyncMutex` and returns `SyncError.SyncAlreadyRunning` when the podcast channel holds it | `core/usecase/ManualSyncNow.kt:50-53` |
| Successful sync calls `onSyncSuccess = markInboxNotesChanged` | `app/App.kt:167` |
| `onInboxMutated` fires **only** on delete success, not on `refresh()` — so no auto-sync feedback loop exists today | `app/InboxViewModel.kt:119-123` |
| Spec says the opposite of what we want, in three places | `sync-hardening-and-recovery.md` §Foreground sync-status refresh + §Out of scope ("inbox sync-on-save"); `AGENTS.md` ("read-only remote `fetch` for the shell indicator is allowed on foreground") |

**Module-size headroom** (cap = `max(400, merge-base size)`): `AppSyncViewModel.kt` 252, `App.kt` 344,
`AppShell.kt` 161, `AppBootEffects.kt` 52, `ShellSyncIndicatorMapping.kt` 56. `App.kt` has the least
headroom — keep additions there to parameters, not logic.

## 3. Design decisions

### 3.1 The spinner must be concentric *by construction*, not by luck

Rotating `Icons.Filled.Sync` is rejected: a Material vector's drawn centroid does not coincide with its
24×24 viewport center, so rotating the layer produces exactly the wobble this plan exists to avoid.

Instead, draw the glyph in a Compose `Canvas` where every coordinate derives from `center` and one
`radius`, and rotate **inside** the draw scope with an explicit pivot:

```kotlin
// feature/sync/SyncSpinner.kt  (sketch — the geometry contract, not final code)
@Composable
fun SyncSpinner(modifier: Modifier, color: Color, strokeWidth: Dp = 1.5.dp) {
    val angle by rememberInfiniteTransition(label = "sync-spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sync-spinner-angle"
    )
    Canvas(modifier) {                                  // modifier MUST be a square .size(n.dp)
        val stroke = strokeWidth.toPx()
        val radius = (size.minDimension - stroke) / 2f  // one radius for arcs and arrowheads
        rotate(degrees = angle, pivot = center) {       // pivot == circle center: zero wobble
            drawArc(...)                                // two 120° arcs at 0° and 180°
            drawPath(arrowHead(center, radius, ...))    // arrowheads tangent to the same circle
        }
    }
}
```

Non-negotiables for this composable:

1. Every drawn coordinate is a function of `center` and `radius`. No hard-coded offsets, no asset.
2. Rotation happens with `rotate(degrees, pivot = center)` in the `DrawScope` — **not** with
   `Modifier.graphicsLayer { rotationZ = … }`, whose pivot is the *layer* center and therefore
   drifts if the composable is ever non-square.
3. The two arcs are 180° apart and identical, so the figure is rotationally symmetric at 180° — any
   residual asymmetry is visible immediately in review, not on a user's device.
4. Linear easing, `RepeatMode.Restart`, period 1100 ms. `360° ≡ 0°`, so restart is seamless.

### 3.2 Same footprint as the count badge

The spinner renders **in the `badge = { }` slot of the existing `BadgedBox`**, so it inherits the exact
anchor math the count uses. A one-digit `Badge` measures 16×16 dp (M3 min size wins over
6 dp text + 2×4 dp padding). Therefore: `Badge { SyncSpinner(Modifier.size(8.dp)) }` also measures
16×16 dp → pixel-identical placement, and it inherits Badge's container color and shape for free.

**Documented fallback** if 8 dp reads too mushy on device: replace the slot with a custom
`SyncBadge` — a `Box` of exactly `Modifier.size(16.dp)`, `CircleShape`,
`containerColor = MaterialTheme.colorScheme.error`, holding an 11 dp spinner. Same 16×16 footprint,
same anchor, more room for the glyph. Do not change the footprint.

### 3.3 Anti-flicker: minimum visible duration

A sync that finishes in 200 ms would flash the spinner. Hold it visible for at least **450 ms** via a
testable Flow operator (`Flow<Boolean>.holdTrueAtLeast(minMs)`), exposed as
`AppSyncViewModel.syncSpinnerVisible: StateFlow<Boolean>` (`stateIn(viewModelScope)`), so it is unit
testable with `TestScope` virtual time instead of on-device eyeballing.

`ShellSyncIndicatorState` gains `spinning: Boolean`; **`spinning` wins over `badgeText`** in the shell,
so the count reappears only after the hold expires.

### 3.4 One coalescing auto-sync entry point

Add `AppSyncViewModel.requestAutoSync()` — the *only* thing the new triggers call. Rules:

1. **No remote configured** → no-op (no state change, no error).
2. **Preflight blocked** (`!preflight.canSync`, e.g. unsafe local paths) → do **not** sync; run a quiet
   local status refresh so the badge tells the truth, and return. Blocked preflight is not an error state.
3. **Sync already in flight** → set `pendingAutoSync = true` and return; run exactly one more sync when
   the current one completes (coalescing, not queueing — N requests during one sync collapse to 1).
4. **`SyncError.SyncAlreadyRunning`** (the podcast channel holds the shared `GitSyncMutex`) is **not a
   failure**: do not record a failed attempt, do not enter `SyncUiState.Error` — treat it as case 3 and
   retry once the mutex frees.
5. Everything else follows the existing `syncNow()` body — extract it into a shared private
   `runSync(...)` so manual and automatic sync cannot drift apart.
6. All mutable trigger state (`syncJob`, `pendingAutoSync`) is touched only from `viewModelScope`
   (main dispatcher, single-threaded) — no locks, no `@Volatile`, and that must be stated in a comment.

No time-based suppression for auto-sync: "ALWAYS" is the product decision. In-flight coalescing is
correctness, not policy. If it proves chatty in practice, a `minAutoSyncIntervalMs` knob is the
one-line follow-up — do not build it speculatively.

### 3.5 Boot sync must not touch the sacred path

The startup invariant (AGENTS.md, `boot-optimization.md`) forbids git `fetch` before the first frame.
So the boot sync fires **after launch settles**, not at gate `Ready`: plumb the existing
`AppLaunchSettledEffect` result (`AppRoot.kt:146-152`) down as a `launchSettled: Boolean` into `App` →
`AppBootEffects`, and trigger there.

The foreground observer must **skip its first `ON_START`** (a cold start fires both), so boot syncs
exactly once. Use an explicit `var firstStart = true` in the observer, not a timing guard.

### 3.6 What explicitly does not change

- `ManualSyncNow`, `SyncPodcastChange`, `SyncPodcastChangesViaVaultSync`, the git mutex, and every
  `data/git/**` internal: **untouched**. This plan changes *when* sync runs, never *how* it runs.
- No WorkManager / AlarmManager. Scheduled background sync stays Tier D (app-contract §out of scope).
  Every trigger here is foreground work tied to a user action or a lifecycle event.
- Podcast mark-as-played keeps its fast-forward-only channel.

## 4. Phases

Each phase is one PR with its own disposable work doc (`plan-next-pr` skill). Model roster per that
skill: **Composer** (Cursor Composer), **Cursor Grok**, **Sonnet** (Claude Sonnet 5), **Terra** (GPT
multi-file implementer), **Opus** (Claude Opus 5), **Sol** (GPT high-reasoning — the house danger-zone
model). Effort is medium unless a step says `high`.

### Phase A — Sync spinner in the badge slot (G2, agent-drivable)

Self-contained and demoable with today's manual sync — no dependency on B or C.

1. Add `feature/sync/SyncSpinner.kt` per the §3.1 geometry contract (square-only, `rotate(pivot = center)`,
   two 120° arcs 180° apart + tangent arrowheads).
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug`
2. Add `feature/sync/SyncSpinnerVisibility.kt` (`Flow<Boolean>.holdTrueAtLeast`) + a colocated test
   using `TestScope` virtual time (0 ms sync still holds ≥450 ms; long sync is not truncated).
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*SyncSpinnerVisibility*'`
3. Expose `AppSyncViewModel.syncSpinnerVisible: StateFlow<Boolean>`; add `spinning` to
   `ShellSyncIndicatorState`; make `spinning` win over `badgeText` in `shellSyncIndicatorState`;
   render `Badge { SyncSpinner(Modifier.size(8.dp)) }` in the `BadgedBox` slot of `AppShell.kt`.
   Extend `ShellSyncIndicatorMappingTest`.
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*ShellSyncIndicatorMapping*'`
4. Device pass: run a manual sync, capture a screen recording, and verify (a) the spinner's center pixel
   does not move between frames, (b) it occupies the same spot as a one-digit count badge. Apply the
   §3.2 fallback only if legibility fails.
   — **Model: — (human, device session)** — check: frame-by-frame comparison of the recording; badge
   center must be static across ≥30 frames.
5. Gate: `./scripts/check-module-budgets.sh` then
   `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`.
   — **Model: Cursor Grok** — check: the two commands above, both green.

**Acceptance:** during any sync the badge slot shows the rotating glyph and no number; within ~450 ms
after it ends the number/dot returns; no visible wobble; no other shell chrome moved.

### Phase B — Every vault write starts a sync (G3 — agent proposes, human applies)

G3 rules bind: tests **in the same PR**, maintainer + second-model review, no autonomous execution.
Run `review-markdown-integrity-data-loss-prevention` and `review-state-consistency-coroutine-safety`
on the diff before merge.

1. Implement `AppSyncViewModel.requestAutoSync()` per §3.4: extract the shared `runSync(...)`, add
   coalescing (`pendingAutoSync`), the remote-not-configured and blocked-preflight skips, and the
   `SyncAlreadyRunning`-is-not-a-failure rule. Ship the diff **with** its invariant argument.
   — **Model: Sol** — high: coroutine re-entrancy + shared-git-mutex interaction
   — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*AppSyncViewModelTest*'`
2. Tests in the same commit, in `AppSyncViewModelTest`: N requests during one sync → exactly 2 syncs;
   blocked preflight → 0 syncs and no `Error` state; `SyncAlreadyRunning` → no recorded failure and one
   retry; no remote → no-op; a failed auto-sync leaves the badge in `"!"` and records the attempt.
   — **Model: Sol** — high: these are the invariants the feature is judged on
   — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*AppSyncViewModelTest*'`
3. Second-model review of steps 1–2 (fresh session, diff + invariant argument, no context from the
   authoring session).
   — **Model: Opus** — high: adversarial read of a sync-trigger diff
   — check: written review verdict recorded in the PR description.
4. Wire the three call sites to `requestAutoSync()`: `ShellNewNoteInputState.kt:62`,
   `AppNavGraph.kt:345`, `AppInboxRoute.kt:68`. Keep `markInboxNotesChanged` and the FTS
   `touchVaultSearchPathsAsync` calls exactly as they are.
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:testDebugUnitTest`
5. Feedback-loop regression test: auto-sync success → `markInboxNotesChanged` → inbox `refresh()` must
   **not** re-enter `onInboxMutated` (today's code is safe — `InboxViewModel.kt:119-123` — pin it).
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*InboxViewModelTest*'`
6. Spec updates in the same PR: delete "inbox sync-on-save" from `sync-hardening-and-recovery.md`
   §Out of scope, rewrite its "After inbox note create or save … local-only" bullet, and update
   `app-contract.md`'s sync-moments wording.
   — **Model: Opus** — check: `rg -n "sync-on-save|local-only" specs/` returns nothing stale.
7. Gate: budgets + full gate (`AppSyncViewModel.kt` must stay under 400 lines; if it crosses, extract
   an `AutoSyncCoordinator` rather than raising a cap).
   — **Model: Cursor Grok** — check: `./scripts/check-module-budgets.sh && ./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`

**Acceptance:** saving, creating, or deleting a note starts a sync (spinner from Phase A proves it);
rapid successive writes produce at most one follow-up sync; offline writes never show a toast, only `"!"`.

### Phase C — Boot and foreground always sync (G3 — agent proposes, human applies)

1. Plumb `launchSettled: Boolean` from `AppRoot` (`AppLaunchSettledEffect`) through `App` into
   `AppBootEffects`. Parameters only in `App.kt` — no logic (headroom: 56 lines).
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:ktlintCheck :app:lintDebug`
2. Replace the boot `refreshShellStatusQuietly(forceRemote = true)` with a launch-settled-gated
   `requestAutoSync()`, and the `ON_START` handler with `requestAutoSync()` that skips its first event
   (§3.5). Extract the decision into a pure `shouldAutoSyncOnLifecycleEvent(event, isFirstStart)` so it
   is testable without a lifecycle host.
   — **Model: Sol** — high: startup-invariant + lifecycle ordering, on the sacred path
   — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*AppBootEffects*'`
3. Tests: boot fires exactly one sync and only after settle; first `ON_START` does not double-fire;
   every subsequent foreground return fires one.
   — **Model: Sonnet** — check: `./scripts/gradle.sh :app:testDebugUnitTest --tests '*AppBootEffects*' --tests '*AppLaunchSettled*'`
4. Cold-start measurement via the `android-performance-debug-loop` skill: before/after on the same
   device, same conditions; first-frame time must not regress. Persist the numbers under
   `specs/performance/`.
   — **Model: Opus** — check: the skill's measurement recipe; recorded before/after deltas.
5. Docs in the same PR: `AGENTS.md` sync-channels bullet (the read-only-fetch-only claim is now false),
   `boot-optimization.md` (boot sync is deferred behind launch-settled and why),
   `app-contract.md` (sync moments), and parity-plan bookkeeping — mark **P1b closed by this plan** in
   `studio-feature-parity.md` §3/§4 and update the m4b gate wording that depends on P1b.
   — **Model: Opus** — check: `rg -n "P1b" specs/` shows no open references.
6. Gate: budgets + full gate.
   — **Model: Cursor Grok** — check: `./scripts/check-module-budgets.sh && ./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest`

**Acceptance:** cold start syncs once, shortly after the home screen appears; every return from
background syncs once; screen-off/on round trips do not stack syncs; no cold-start regression.

### Phase D — Absorb and delete

Move the durable outcome into `app-contract.md` (sync moments = writes + boot + foreground; still no
scheduler) and `sync-hardening-and-recovery.md` (auto-sync trigger rules, coalescing, failure silence),
then delete this plan and its rows in `specs/plans/README.md`.
— **Model: Opus** — check: `rg -n "always-on-sync" specs/` returns only the README removal diff.

## 5. Risks and how each is closed

| Risk | Closure |
|---|---|
| Spinner wobble (the whole point) | §3.1 geometry contract + Phase A step 4 frame comparison |
| Spinner flicker on fast syncs | 450 ms hold, unit-tested with virtual time (§3.3) |
| Badge jumps when the count is replaced | Identical 16×16 footprint in the same `BadgedBox` slot (§3.2) |
| Sync storms from rapid writes | In-flight coalescing collapses N requests to 1 follow-up (§3.4 rule 3) |
| Contention with the podcast sync channel | `SyncAlreadyRunning` treated as "retry after", never as failure (§3.4 rule 4) |
| Auto-sync error states hijacking the UI | Errors only reach the badge (`"!"`) + Sync screen; no toasts (product decision) |
| Blocked preflight (unsafe paths) silently retrying forever | Blocked preflight does not sync at all and does not set `pendingAutoSync` |
| Cold-start regression | Launch-settled gate (§3.5) + Phase C step 4 measurement |
| Inbox UI churn / scroll reset from `markInboxNotesChanged` firing far more often | Phase B step 5 regression test + device check during Phase B acceptance |
| Battery/data on metered networks | Accepted: every trigger is foreground work tied to a user action or an app-open; no scheduler is added |

## 6. Deletion condition

Delete when Phases A–C have landed, `app-contract.md` and `sync-hardening-and-recovery.md` describe
the new sync moments, `AGENTS.md`'s sync bullet is accurate, and `studio-feature-parity.md` records
P1b as closed by this plan.

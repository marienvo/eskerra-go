# Podcast player fixes — phased plan

Status: proposed (2026-06-21)
Scope: four reported podcast-player bugs plus a deliberate scope add (Android media
notification with artwork). Decisions confirmed with the product owner:

- **Episode switching:** lock the episode list while audio is *playing*; the user must
  pause (or archive/close the player) before starting a different episode.
- **App close:** keep playing in the background; re-attach to the live `MediaSession`
  and fully resync state when the user returns (via the notification or relaunch).
- **Notification:** in scope — show artwork, title/series, and transport controls on the
  notification and lockscreen.

Each step is tagged with a suggested model:

- **Composer** — mechanical, local, fully-specified edits (UI affordances, wiring, simple guards).
- **GPT** — moderate-reasoning, multi-file but well-scoped (notification metadata, glue, tests).
- **Opus High** — deep state-machine / concurrency / race-condition reasoning.

Quality gate after every phase (per AGENTS.md):
```bash
./scripts/check-module-budgets.sh
./scripts/gradle.sh :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest
```

---

## Root-cause summary

| # | Symptom | Root cause |
|---|---------|-----------|
| 1 | Pause then play "closes" the player | While paused the ETag poller re-activates (it only runs while *not* playing). A remote-playlist change (notably the clear that fires when paused < `MIN_PROGRESS_MS` = 10s) triggers `syncPlaylistFromRemote()` → `resetPlaybackIfIdle()` → `stop()`, which resets to `PodcastPlaybackState()` and unmounts the mini player. `resetPlaybackIfIdle` wrongly treats a *user-paused* session as a disposable resume-hint. |
| 2 | Launch from notification doesn't land in Podcasts mode with the player open | `RestorePodcastPlayback` reads `currentNativeSession()`, but `Media3PodcastPlayerDriver` connects its `MediaController` asynchronously and `controller` is still `null` at restore time. Routing + state are decided from the persisted snapshot (phase `PRIMED`, possibly stale) instead of the live, actually-playing session. |
| 3 | App close vs Android player don't sync | `onDestroy()` releases only the controller; the `MediaSessionService`/ExoPlayer keeps playing (correct). But there is no clean "re-attach to live session and fully resync" path on return — same root as #2. Behaviour on task-swipe is also undefined. |
| 4 | Switching episode mid-playback updates series but not title/artwork | Needs device repro to pin the rendering glitch; per decision we sidestep it by gating switching behind pause rather than supporting hot-swap. |

Design principle introduced in Phase 1–2: **the live in-app session is the single source
of truth.** Remote/persisted playlist data may only *hydrate* when there is no active local
session; it must never tear one down. On (re)connect, the live `MediaController` snapshot
wins over persisted snapshots.

---

## Phase 0 — Characterization tests (lock in current behaviour) — **GPT**

Before touching logic, add failing/【pending】tests that encode the desired behaviour so the
fixes are verifiable without a device where possible.

- `PodcastsViewModelTest`: pause an active episode (both < 10s and > 10s progress), then
  deliver a playlist-generation change → assert the session is **not** stopped and
  `hasActiveEpisode` stays true. (Currently fails for the < 10s case.)
- `PodcastPlayerMachineTest`: assert `resetPlaybackIfIdle`-equivalent transitions don't drop
  an active PAUSED/NEAR_END_PAUSED episode.
- `RestorePodcastPlaybackTest`: when a live native session is playing, restore returns
  `preferredShellMode = PODCASTS` and a PLAYING-consistent state (drives Phase 2).

Files: `app/src/test/java/com/eskerra/go/feature/podcasts/`,
`app/src/test/java/com/eskerra/go/core/...`

Acceptance: tests compile and express the target; the < 10s pause test is red.

---

## Phase 1 — Stop remote sync from tearing down an active session (Bug 1) — **Opus High**

The hard part is concurrency between the player state machine and the foreground poller.

1. **`PodcastsViewModel.resetPlaybackIfIdle()`** — only act on disposable sessions. Replace
   the `!isPlaying && hasActiveEpisode` condition so it never stops a user-driven session:
   only clear when phase is `PRIMED` or `STOPPED` (a hydrated hint that was never actually
   played), never when `PAUSED` / `NEAR_END_PAUSED` / `LOADING` / `PLAYING`.

2. **`hydrateOrReset` / `syncPlaylistFromRemote`** — bail when there is an active local
   session, not just when playing. Change the `if (current.isPlaying) return` guards to
   `if (current.hasActiveEpisode && current.phase != IDLE) return` so a paused, visible
   session is authoritative over remote.

3. **Pause should not clear the remote playlist just because progress < 10s.** In
   `pausePlayback()` and `AppPodcastBootstrap.persistSnapshotAfterUserAction`, persist a
   resumable snapshot for any actively-paused episode regardless of position; only clear
   remote/local on explicit **stop**/**archive**. (Reconsider `MIN_PROGRESS_MS` gating —
   it exists to avoid persisting "barely started" sessions, but an explicit user pause is
   intent to keep.) Keep the 10s gate only for the *auto*/lifecycle persistence path, not
   the explicit pause path.

4. Make the two pause paths consistent: the mini player calls `bridge.pausePlayback`
   (`AppPodcastBootstrap`), while `PodcastsViewModel.pausePlayback()` has divergent
   clear-on-<10s logic. Collapse to one rule (keep session on explicit pause).

Acceptance: Phase 0 pause tests pass. Manual: pause at 3s and at 60s → mini player stays,
play resumes from the paused position. R2-configured and non-R2 both fine.

Watch-outs: `PlaylistR2PollingHost.setPlaybackActive(isPlaying)` keeps polling alive while
paused by design (so other devices' changes arrive) — that's fine once #1/#2 stop the
teardown. Don't disable polling-while-paused; fix the reducer/guards instead.

---

## Phase 2 — Live session as the source of truth on launch & reconnect (Bug 2 + Bug 3) — **Opus High**

The controller connects async; restore currently races it. Make restore await/observe the
live session.

1. **`Media3PodcastPlayerDriver`** — expose connection completion. Either:
   - add `suspend fun awaitConnection()` that awaits `controllerFuture`, or
   - make `currentNativeSession()` suspend and await connection before reading.
   On connect, `publishNativeSnapshot` already runs — ensure it fires for a pre-existing
   loaded item so a relaunch into a playing session immediately emits PLAYING.

2. **`RestorePodcastPlayback`** — await the live session, then prefer it over snapshots
   (`reconcilePodcastPlaybackSources` already prioritizes native session; the bug is that it
   was null at call time). When the live session is playing/paused, return
   `preferredShellMode = PODCASTS` and let the live snapshot, not the persisted one, set the
   phase (so the UI shows PLAYING with the live position, not a stale PRIMED).

3. **`AppPodcastBootstrap`** — the initial-route `LaunchedEffect` must run *after* restore
   has the live session. If awaiting is undesirable on the hot path, add a secondary
   re-route: when the driver first emits a PLAYING/PAUSED live state and the shell is not in
   Podcasts mode at cold start, navigate to `PODCASTS_GRAPH`. Guard with
   `initialNavigationDone` so it doesn't fight user navigation later.

4. **App close / return (Bug 3).** Confirm `onDestroy()` keeps the service alive (it only
   releases the controller — keep it that way). On return, a fresh controller connects to the
   running session and (per step 1) publishes the live snapshot → full resync for free. Add a
   test/assertion that `release()` does **not** call `MediaController.stop()`/`clearMediaItems`.

5. **Task-swipe behaviour.** Override `PodcastPlaybackService.onTaskRemoved`: keep playing if
   the player is playing; otherwise stop self and remove the notification (matches the
   "keep playing, resync on return" decision and avoids a dangling paused notification).

Acceptance: start playback, background the app, tap the notification → app opens in Podcasts
mode with the player open, live position, PLAYING state. Cold launch with a resumable
*paused* session → Podcasts mode, player open, paused at saved position.

Watch-outs: don't block the splash indefinitely on `awaitConnection()` — bound it
(timeout → fall back to snapshot routing). `shouldDismissSplashWithoutInbox` already keys
off the podcasts route; keep splash behaviour intact.

---

## Phase 3 — Gate episode switching behind pause (Bug 4) — **Composer** (VM guard: **GPT**)

Per decision: while audio is *playing* the list is locked; pausing (or archive/close) unlocks
switching.

1. **`PodcastsViewModel.onEpisodeClick`** — when there is an active session that
   `isPlaying` (or `LOADING`) for a *different* episode, ignore the start and surface a
   transient hint instead. Allow switching freely when the session is paused/stopped/idle, or
   when re-tapping the active episode (existing no-op). Add a one-shot UI event
   (e.g. `PodcastsUiState`/event channel) carrying a hint message. **GPT** (touches state).

2. **UI affordance** (`PodcastsScreen` rows + `PodcastMiniPlayer`) — when playing, render
   episode rows as visually disabled (reduced alpha / non-clickable) and show the hint
   ("Pause to play another episode") on tap, e.g. a short snackbar or inline text. Keep the
   active-episode row visually distinct. **Composer**.

3. Make sure pausing immediately re-enables the rows (drive purely off `playerState.isPlaying`
   so it's reactive). **Composer**.

Acceptance: while playing, tapping another episode does nothing but shows the hint; rows look
disabled. After pause, tapping another episode starts it. No regression to multi-select /
archive selection.

Note: this deliberately sidesteps the title/artwork-staleness rendering glitch by removing
hot-swap. If hot-swap is ever wanted later, file a separate task with device repro first.

---

## Phase 4 — Android media notification: artwork + lockscreen controls (scope add) — **GPT** (artwork URI resolution: **Opus High**)

1. **Artwork in metadata** — in `Media3PodcastPlayerDriver.play()` set
   `MediaMetadata.artworkUri` (and/or artworkData) from the resolved local artwork for the
   episode's `rssFeedUrl`. Source the local file URI via `LoadPodcastArtwork.peek/resolve`.
   Because resolution may be async/disk-backed, resolve before/around `setMediaItem` or update
   the item's metadata once resolved. **Opus High** (async ordering vs. the state machine).

2. **Notification + controls** — `PodcastPlaybackService` currently uses the default
   `MediaSessionService` notification. Ensure title/series/artwork show, and add the ±10s
   seek commands to the session's available commands so they appear as notification/lockscreen
   actions (custom `MediaSession.Callback` / `MediaNotification.Provider` if the defaults are
   insufficient). **GPT**.

3. Verify metadata updates when the episode changes (it can only change after pause per
   Phase 3, so no hot-swap race). **GPT**.

Acceptance: notification and lockscreen show artwork, title, series, and working
play/pause/±10s; controls drive the same state the in-app player reflects.

Watch-outs: `MainActivity`'s Coil image loader is for in-app UI; the notification artwork must
be a `Uri`/bitmap Media3 can load (local cached file path works). Respect the existing artwork
cache; don't add a second fetch path.

---

## Phase 5 — Verification — **GPT**

- Full quality gate (budgets, ktlint, lint, unit tests).
- Device pass via `./scripts/install-debug.sh` covering: pause/resume (early + late),
  background+notification return, cold launch into paused/playing session, task-swipe,
  episode switching gate, notification artwork/controls.
- Use the `/verify` skill for the device walkthrough.

---

## File map (anticipated touch points)

- `core/player/PodcastPlayerMachine.kt` — reducer guards (Phase 1).
- `feature/podcasts/PodcastsViewModel.kt` — `resetPlaybackIfIdle`, `hydrateOrReset`,
  `syncPlaylistFromRemote`, `pausePlayback`, `onEpisodeClick` (Phases 1, 3).
- `app/AppPodcastBootstrap.kt` — pause persistence rule, re-route on live state (Phases 1, 2).
- `core/usecase/RestorePodcastPlayback.kt` — await/prefer live session (Phase 2).
- `data/player/Media3PodcastPlayerDriver.kt` — connection await, artwork metadata (Phases 2, 4).
- `data/player/PodcastPlaybackService.kt` — `onTaskRemoved`, notification/controls (Phases 2, 4).
- `feature/podcasts/PodcastsScreen.kt`, `PodcastMiniPlayer.kt` — disabled rows + hint (Phase 3).
- `MainActivity.kt` — confirm `onDestroy` keeps service alive (Phase 2).
- Tests under `app/src/test/java/com/eskerra/go/...` (Phases 0–4).

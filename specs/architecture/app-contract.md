# Eskerra Go app contract

Product behavior and boundaries for the native Android app. Non-obvious rules that code alone does not express belong here.

## Core capabilities

- Git-first workspace setup (one workspace per install).
- Clone from `file://` or sanitized `https://` remotes; vault sync for HTTPS remotes only.
- Inbox list from markdown files; create and edit inbox notes; non-inbox notes read-only.
- Markdown reader with clickable wiki links (title or filename stem, case-insensitive; path-like targets stay case-sensitive).
- Full-text vault search (SQLite FTS5).
- HTTPS remote sync: commits all local vault changes; integrates remote via fast-forward or auto-merge with conflict sidecars. Runs on the sync button and automatically on note writes, boot, and foreground return — always foreground work, never a background scheduler.
- Podcast episodes tab: catalog, playback, R2 playlist handoff, RSS refresh, mark-as-played.
- Floating shell navigation with tab state preservation.

## Share target (other apps → inbox draft)

Eskerra appears in the Android share sheet for **`text/plain` only** (`ACTION_SEND`; no `ACTION_SEND_MULTIPLE`, no images or other MIME types). The chooser label is "Eskerra"; the launcher stays "Eskerra Go".

A share prefills the existing compose pill and **never saves by itself** — the user still presses send, exactly as for a note typed by hand.

Line 1 of the pill becomes the note's h1 **and its filename**, which drives the two shapes:

| Shared | Line 1 | Line 3 | Caret |
|---|---|---|---|
| Anything with a usable `EXTRA_SUBJECT` | the subject (sanitized, ≤120 chars) | the shared text | end of line 1 |
| A bare URL whose page title was fetched | the fetched `<title>` | the URL | end of line 1 |
| A bare URL with no title available, or plain text | *(empty — user types it)* | the shared text | offset 0 |

Rules that follow from that:

- **A URL is never line 1.** "It is a URL" means the whole trimmed share is one `http(s)` token; a link inside prose is body text.
- **`EXTRA_SUBJECT` wins and costs no network call.** The page `<title>` is fetched only when no usable subject arrived; it is bounded (4 s, 256 KB, textual responses only) and any failure is silent — the draft is already usable, and the user simply types the title.
- **A late-arriving fetched title is applied only while the draft is untouched** — still byte-for-byte what the app wrote for that share, with no save in flight. One keystroke and it is dropped silently.
- **A share never destroys typed text**: it takes a blank draft, otherwise it appends below. A share arriving mid-save is replayed after the save settles.
- **A share always lands where the pill is visible**: it wins over resumable podcast playback on a cold start, pops out of search (the pill drives the query there), and leaves the user in place on the inbox or a note reader.
- If the workspace is not set up yet, the share waits and is delivered once setup completes; it is not persisted across process death.
- `MainActivity` is `singleTask`, so a share reuses the single existing instance instead of building a second composition root. Consequence: **Back after a share returns to the launcher**, not to the sharing app.

## Compose pill focus

A completed inbox-note save releases the field's focus and lets the keyboard go — for every note, not only shared ones. A failed save keeps focus so the error can be fixed in place.

## Shell bottom navigation

Top-level tab switches use `popUpTo(inbox) { saveState = true }`, `launchSingleTop`, and `restoreState` so sibling stacks (Podcasts, Menu, Search) retain state across round trips. Re-tapping the active tab is a no-op.

Home (inbox) re-selection is decided by [resolveTabNavigation](app/src/main/java/com/eskerra/go/app/AppNavigation.kt) (unit-tested in [AppNavigationTest.kt](app/src/test/java/com/eskerra/go/app/AppNavigationTest.kt)):

| Current route | Home tap | Behavior |
|---------------|----------|----------|
| `inbox` | Home | No-op |
| `note/*` or `editor/*` | Home | Pop to inbox **and reset** (Today Hub → current week, scroll top) |
| Podcasts / Search / Menu | Home | Pop to inbox **and restore** last home view |
| `podcasts` | Podcasts | No-op |

## Inbox note scan rules

When indexing markdown files for the Inbox list:

- The scanner skips `.git` subtrees entirely during filesystem traversal.
- Symlinked markdown files and symlinked directories are not followed or indexed.
- Rationale: a cloned workspace must not read app-private files outside the workspace via symlinks (for example credentials stored under the same app-private `filesDir`).

Inbox list order: last modified descending.

Inbox cold start may show the last cached inbox list briefly while the workspace rescan runs in the background (see [boot-optimization.md](boot-optimization.md)).

## Git write and sync channels

All JGit mutations share one process-wide mutex so vault sync and podcast auto-sync never overlap. Details: [sync-hardening-and-recovery.md](sync-hardening-and-recovery.md).

| Channel | Trigger | Staged paths | Integration | Push |
| --- | --- | --- | --- | --- |
| Vault sync | User taps sync, **any note write** (inbox create, editor save, inbox delete), **boot**, or **foreground return** | All safe local changes | FF when behind; auto-merge on divergence | Yes, with retry |
| Podcast RSS refresh | Pull-to-refresh | RSS writes `General/`; then vault sync engine | Same as vault sync | Same as vault sync |
| Podcast mark-as-played | Checkbox | Changed podcast paths under `General/` only | **Fast-forward only** | Best-effort; pending on divergence |

**Sync moments.** Note writes, app boot, and every foreground return always start a vault sync — the
same code path as the button, with coalescing so rapid triggers collapse to one follow-up. Automatic
syncs fail **silently**: the `"!"` shell badge and the sync screen carry the detail; there are no
toasts. Trigger rules (coalescing, blocked preflight, mutex contention) are in
[sync-hardening-and-recovery.md](sync-hardening-and-recovery.md#automatic-vault-sync-triggers).
Boot's sync waits until launch has settled and one frame has been drawn, keeping Git and network work
off the startup path ([boot-optimization.md](boot-optimization.md)); the first `ON_START` of the
process is boot's, so it does not sync twice.

All of this is foreground work tied to user actions, never a background scheduler.

Vault sync (button, note writes, and RSS refresh) auto-merges diverged histories with conflict sidecars. Podcast mark-as-played never auto-merges, rebase, or reset.

## Shell sync indicator

One badge slot on the hamburger carries the whole sync story, and it never changes size — the pending-change count, the `"!"` after a failure, and the spinner all occupy the same 16×16 dp `Badge` inside the existing `BadgedBox`, so nothing shifts as the state changes. While a sync runs, the spinner **replaces** the count; the count returns when the spinner stops.

- The spinner is drawn ([SyncSpinner.kt](app/src/main/java/com/eskerra/go/feature/sync/SyncSpinner.kt)), not a rotated asset: every coordinate derives from the draw scope's `center` and one radius, and rotation uses `rotate(pivot = center)` rather than `Modifier.graphicsLayer { rotationZ }`, whose pivot is the layer's center and drifts when the composable is not perfectly square. A rotated vector icon wobbles because its drawn centroid is not its viewport center; that wobble is the reason this composable exists.
- Spinner visibility is held for a minimum duration (`holdTrueAtLeast`) so a sync that finishes in 200 ms does not flash it. The hold is a Flow operator, not a UI timer, so it is unit-testable with virtual time.

## Sync branch alignment

Configured sync branch, local checkout, and `origin/<branch>` must stay aligned for manual sync. See [sync-branch-alignment.md](sync-branch-alignment.md).

## Explicitly out of scope

- Multi-workspace support.
- Automatic merge/rebase/conflict-resolution UI.
- SSH remotes.
- WorkManager / AlarmManager scheduled background sync.
- iOS.

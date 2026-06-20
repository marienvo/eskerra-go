# Eskerra Go app contract

Product behavior and boundaries for the native Android app. Non-obvious rules that code alone does not express belong here.

## Core capabilities

- Git-first workspace setup (one workspace per install).
- Clone from `file://` or sanitized `https://` remotes; manual inbox sync for HTTPS.
- Inbox list from markdown files; create and edit inbox notes; non-inbox notes read-only.
- Markdown reader with clickable wiki links (title or filename stem, case-insensitive; path-like targets stay case-sensitive).
- Full-text vault search (SQLite FTS5).
- Manual HTTPS remote sync (explicit user action): inbox-only commits, fast-forward integration.
- Podcast episodes tab (see [`android-podcasts-rebuild-spec.md`](../plans/android-podcasts-rebuild-spec.md)): catalog, playback, R2 playlist handoff, RSS refresh, mark-as-played.
- Floating shell navigation with tab state preservation.

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

All JGit mutations share one process-wide mutex so manual inbox sync and podcast auto-sync never overlap. Details: [sync-hardening-and-recovery.md](sync-hardening-and-recovery.md).

| Channel | Trigger | Staged paths | Commit | Push |
| --- | --- | --- | --- | --- |
| Manual inbox sync | User taps sync | `Inbox/` only | One commit if inbox dirty | After fetch + fast-forward when ahead |
| Podcast auto-sync | Mark-as-played or RSS refresh complete | Changed podcast paths under `General/` only | **One commit per operation** (separate message) | Same fetch + FF + push rules |

Podcast auto-sync is foreground work tied to user actions, not a background scheduler.

Integration is **fast-forward only**. Diverged histories require manual Git repair; the app does not auto-merge, rebase, or reset.

## Sync branch alignment

Configured sync branch, local checkout, and `origin/<branch>` must stay aligned for manual sync. See [sync-branch-alignment.md](sync-branch-alignment.md).

## Explicitly out of scope

- Multi-workspace support.
- Automatic merge/rebase/conflict-resolution UI.
- SSH remotes.
- WorkManager / AlarmManager scheduled background sync.
- iOS.

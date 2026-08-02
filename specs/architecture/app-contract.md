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

## Sync branch alignment

Configured sync branch, local checkout, and `origin/<branch>` must stay aligned for manual sync. See [sync-branch-alignment.md](sync-branch-alignment.md).

## Explicitly out of scope

- Multi-workspace support.
- Automatic merge/rebase/conflict-resolution UI.
- SSH remotes.
- WorkManager / AlarmManager scheduled background sync.
- iOS.

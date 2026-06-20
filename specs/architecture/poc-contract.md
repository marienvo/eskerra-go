Eskerra Go PoC proves:
- Git-first setup
- One workspace
- Clone/pull/read/write/commit/push path
- Inbox list from markdown files
- Add opens a compose form; Save creates an editable inbox note under `Inbox/` using line 1 as title, H1, and sanitized filename
- Inbox list order: last modified descending
- Inbox cold start may show the last cached inbox list briefly while the workspace rescan runs in the background (see [boot-optimization.md](boot-optimization.md))
- Markdown reader supports clickable wiki links
- Wiki links resolve by note title or filename stem case-insensitively; path-like targets stay case-sensitive and normalize `.` path segments for registry lookup
- Non-inbox notes are read-only
- Floating shell navigation exists

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

## Sync branch alignment

Configured sync branch, local checkout, and `origin/<branch>` must stay aligned for manual sync. See [sync-branch-alignment.md](sync-branch-alignment.md).

Explicitly out of scope:
- Full-text search
- Background sync
- Multi-workspace
- Conflict resolution
- Podcast playback
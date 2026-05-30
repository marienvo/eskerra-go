Eskerra Go PoC proves:
- Git-first setup
- One workspace
- Clone/pull/read/write/commit/push path
- Inbox list from markdown files
- Add creates editable inbox note
- Markdown reader supports clickable wiki links
- Non-inbox notes are read-only
- Floating shell navigation exists

## Inbox note scan rules

When indexing markdown files for the Inbox list:

- The scanner skips `.git` subtrees entirely during filesystem traversal.
- Symlinked markdown files and symlinked directories are not followed or indexed.
- Rationale: a cloned workspace must not read app-private files outside the workspace via symlinks (for example credentials stored under the same app-private `filesDir`).

Explicitly out of scope:
- Full-text search
- Background sync
- Multi-workspace
- Conflict resolution
- Podcast playback
- Dashboard content
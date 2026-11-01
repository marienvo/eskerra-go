# Inbox snooze

Inbox notes can be deferred from Noteflow. Studio offers tomorrow at 09:00; Go accepts a
calendar date from tomorrow onward and uses 09:00. The time zone is the one on the device
where snooze was chosen; the stored instant is shared across devices.

The note's YAML frontmatter stores `snoozed_until` as a quoted RFC 3339 UTC timestamp, for
example `snoozed_until: "2026-11-02T08:00:00Z"`. A future valid value hides only the note's
Noteflow inbox row. Missing, ambiguous, invalid and expired values do not hide the note. Search
and vault navigation remain available. Expiry never writes to the vault. Anchored or aliased
snooze values are unsupported and never changed automatically.

Bulk snooze reports per-note failures; a failed file remains unmodified. Writes modify only the
owned property and use the shared Git mutex. A successful write evicts cached content, refreshes
the registry and requests the normal automatic vault sync. The registry and inbox snapshots
carry the deadline; old snapshots remain readable and are revalidated after launch settles.

Go uses SnakeYAML's safe parser to validate existing YAML without round-tripping unrelated user
content. Parsing runs on the existing background inbox registry refresh, after launch settlement;
it does not add a vault-wide scan. Inbox notes are hashed during background indexing to guard against stale selections; their
leading frontmatter is parsed without reading unrelated Markdown into memory. Unchanged registry
entries reuse their cached hash and deadline. This adds parser and file-read cost during refresh;
measure `ColdStartTrace` first-frame and registry scan duration, plus visited/re-read counts on a
representative vault, before release. Device measurements remain necessary when a device is available.

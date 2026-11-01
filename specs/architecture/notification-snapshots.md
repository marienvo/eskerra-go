# Notification snapshots

Notification delivery is implemented separately. This layer stores candidate occurrences and a
queue for the delivery layer. It is disabled by default and performs its first complete scan after a
successful sync when enabled. The future settings use case must request an immediate vault sync and
reconcile the periodic scheduler after enabling; it must reconcile after disabling to cancel the job. The first successful scan is a silent baseline. Later scans queue new
inbox items, elapsed snoozes and newly available incomplete dated-folder notes; an already overdue
reminder discovered for the first time is not queued. Known future reminders become due after their
deadline. Dated notes initially marked complete stay known, so removing the completion mark alone
does not create a new availability event.

The snapshot and pending queue are stored transactionally outside the vault. Candidates missing
from a successful replacement snapshot are removed from the queue. A failed discovery or note read
does not replace the last complete snapshot. Local inbox changes are recorded while holding the Git
mutex before staging, so the app does not announce its own newly created inbox item. If this local
recording fails, notifications are disabled; if the disabled setting itself cannot be persisted,
the sync stops before staging any changes.

Reminder parsing follows the desktop live date-token grammar; date-only reminders use 09:00 in the
device time zone. Per-note parsed reminders are cached by registry mtime, size, inbox hash when
available, and time zone. Dated-folder targets are read on each scan. Cache metadata is a performance
hint and can miss an external rewrite that preserves both size and mtime. No Markdown is modified.

Sync and scanning share the Git mutex to avoid capturing partially integrated or locally written
notes. The first scan reads every indexed note and `.flow` discovery walks the vault; later scans
reuse unchanged reminder parses but still perform marker discovery and read dated targets. Neither
scan is on the first-render path. Measure first-frame time, scan duration, number of notes reread,
mutex hold duration and battery/network use on representative vaults before release.

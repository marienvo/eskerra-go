# Notification snapshots

The notification pipeline stores candidate occurrences and a queue for delivery. It is disabled by default; the sync-settings toggle requests Android notification permission when enabling and performs its first complete scan after a
successful sync when enabled. The settings adapter serializes enable/disable with vault mutations, clears the prior baseline and
pending queue, requests an immediate sync on enabling, and reconciles the periodic scheduler. The first successful scan is a silent baseline. Later scans queue new
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
device time zone. Per-note reminder parsing is cached by a SHA-256 hash of decoded note content and time zone. Every scan
reads each note to verify its hash, including rewrites that preserve size and mtime. No Markdown is
modified.

After each complete scan, due events are gated on the newest valid `lastActivityAt` across all
`presence/desktop/<installationId>.json` R2 objects. Activity less than five minutes old discards
those due occurrences permanently; future occurrences remain queued. Missing R2 configuration or
presence allows delivery. An R2 or settings read failure keeps the queue intact and schedules a
five-minute retry. The activity request runs outside the Git mutex.

Future events use `AlarmManager` exact, idle-tolerant alarms when permission is granted, with an
inexact idle-tolerant fallback otherwise. Boot, package replacement, clock and time-zone changes
reconcile the persisted queue. Deliverable occurrences are acknowledged only after Android accepts
their visible notification. A single occurrence opens its note directly through the existing note
route. Simultaneous occurrences share one expandable notification whose content intent opens an
in-app note picker; each picker row opens the corresponding note. If notification permission is
missing, occurrences stay queued for a later reconciliation.

Sync and scanning share the Git mutex to avoid capturing partially integrated or locally written
notes. The first scan reads every indexed note and `.flow` discovery walks the vault; later scans
still read note content and perform marker discovery, but reuse unchanged reminder parses. Neither
scan is on the first-render path. Measure first-frame time, scan duration, number of notes reread,
mutex hold duration and battery/network use on representative vaults before release.

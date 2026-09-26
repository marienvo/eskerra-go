# Sync hardening and recovery

## Product rules

- Vault sync (`ManualSyncNow`) commits all safe local working-tree changes, fetches and fast-forwards when behind, and auto-merges divergence with conflict sidecars where the remote remains canonical.
- It is triggered by the sync button, every note write, boot, and foreground return. Requests coalesce; automatic requests fail silently to the shell badge.
- Boot sync is deferred until launch has settled and is never on the first-render path. No WorkManager or AlarmManager schedules sync.
- Every JGit mutation shares the process-wide `GitSyncMutex`.
- Before syncing, an interrupted Git operation is recovered according to the vault-sync recovery policy. Unsafe paths fail closed and are never staged.
- Sync uses 30-second transport timeouts and retries a rejected push through integrate-and-push cycles. It never exposes credentials or raw transport errors.

## Write boundary

The app writes only Inbox notes through its note use cases. Other vault content is read-only in the UI; remote integration may update it during vault sync. Vault sync stages all safe changes and never has a special channel for individual generated files.

## Recovery and observability

- Divergent Markdown changes create conflict sidecars; the canonical file stays remote-authoritative.
- A registry refresh failure after successful Git work is a partial success: local notes remain available and UI shows a warning.
- Persist only the latest sync attempt with a safe error category. Diagnostics may include sanitized host, branch, counts and outcome, never tokens, credential URLs, headers, raw exceptions or absolute paths.

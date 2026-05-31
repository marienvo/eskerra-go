package com.eskerra.go.core.model

/** Rich sync status for manual sync and the sync screen. */
enum class SyncStatusState {
    Clean,
    DirtyLocalChanges,
    Ahead,
    Behind,
    Diverged,
    ConflictRisk,
    Unavailable,
    Error
}

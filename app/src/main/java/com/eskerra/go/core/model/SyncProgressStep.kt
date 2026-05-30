package com.eskerra.go.core.model

/** Progress steps shown during manual sync. */
enum class SyncProgressStep {
    ValidatingWorkspace,
    ReadingCredentials,
    InspectingStatus,
    CommittingInboxChanges,
    FetchingRemote,
    IntegratingRemote,
    PushingLocalCommits,
    RefreshingNotes,
    Complete
}

package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnapshotStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Loads inbox summaries with optional cached cold-start reads and snapshot persistence. */
class LoadInboxSummariesCached(
    private val delegate: LoadInboxSummaries,
    private val snapshotStore: InboxSnapshotStore,
    /**
     * Shared registry updates (typically [com.eskerra.go.core.repository.NoteRegistryCachePort.registry]).
     * Inbox UI observes this so a sync-driven registry refresh drops deleted notes even when the
     * Compose `inboxRefreshSignal` path is missed.
     */
    val registryUpdates: StateFlow<NoteRegistry?> = MutableStateFlow(null)
) {

    suspend fun readCached(config: WorkspaceConfig, filesDir: File): List<NoteSummary>? =
        snapshotStore.read(config, filesDir)

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<List<NoteSummary>> = delegate(config, filesDir).onSuccess { summaries ->
        snapshotStore.save(config, filesDir, summaries)
    }
}

package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File

/** Returns inbox note summaries from a refreshed workspace note registry. */
class LoadInboxSummaries(private val registryCache: NoteRegistryCache) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<List<NoteSummary>> = registryCache.refresh(config, filesDir).map { it.inboxSummaries }
}

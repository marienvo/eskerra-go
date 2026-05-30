package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Returns inbox note summaries from a refreshed workspace note registry. */
class LoadInboxSummaries(private val repository: NoteRegistryRepository) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File
    ): Result<List<NoteSummary>> = repository.refresh(config, filesDir).map { it.inboxSummaries }
}

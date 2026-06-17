package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import java.io.File

/** Test fake that fails registry refresh. */
class FailingNoteRegistryRepository(private val delegate: NoteRegistryRepository) :
    NoteRegistryRepository {

    var failRefresh: Boolean = true

    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry?
    ): Result<NoteRegistry> {
        if (failRefresh) {
            return Result.failure(NoteIndexException(NoteIndexError.ScanFailed("refresh failed")))
        }
        return delegate.refresh(config, filesDir, previousRegistry)
    }
}

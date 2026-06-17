package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileNoteRegistryRepository(
    private val scanner: NoteWorkspaceScanner = MarkdownNoteScanner()
) : NoteRegistryRepository {

    override suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        previousRegistry: NoteRegistry?
    ): Result<NoteRegistry> = withContext(Dispatchers.IO) {
        val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
        if (workspaceResult.isFailure) {
            return@withContext Result.failure(
                NoteIndexException(
                    NoteIndexError.InvalidWorkspacePath(
                        workspaceResult.exceptionOrNull()?.message
                    )
                )
            )
        }

        val workspaceDir = workspaceResult.getOrThrow()
        if (!workspaceDir.isDirectory) {
            return@withContext Result.failure(
                NoteIndexException(NoteIndexError.WorkspaceMissing(null))
            )
        }

        scanner.scan(workspaceDir, previousRegistry).fold(
            onSuccess = { registry ->
                Result.success(registry)
            },
            onFailure = { error ->
                if (error is NoteIndexException) {
                    Result.failure(error)
                } else {
                    Result.failure(
                        NoteIndexException(NoteIndexError.ScanFailed(error.message))
                    )
                }
            }
        )
    }
}

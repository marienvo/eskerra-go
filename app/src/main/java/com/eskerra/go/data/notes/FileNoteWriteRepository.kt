package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteWriteRepository
import com.eskerra.go.data.git.WorkspaceGitRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileNoteWriteRepository(private val gitRepository: WorkspaceGitRepository) :
    NoteWriteRepository {

    override suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        markdown: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }

        gitRepository.writeFile(workspaceDir, notePath.value, markdown).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                Result.failure(
                    NoteWriteException(NoteWriteError.WriteFailed(error.message))
                )
            }
        )
    }

    override suspend fun exists(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }

        val noteFile = File(workspaceDir, notePath.value)
        val canonicalWorkspace = workspaceDir.canonicalFile
        val canonicalNote = try {
            noteFile.canonicalFile
        } catch (error: Exception) {
            return@withContext Result.failure(
                NoteWriteException(NoteWriteError.WriteFailed(error.message))
            )
        }

        if (
            !FileNoteContentRepository.isStrictlyInsideWorkspace(
                canonicalWorkspace,
                canonicalNote
            )
        ) {
            return@withContext Result.failure(
                NoteWriteException(NoteWriteError.InvalidNotePath)
            )
        }

        Result.success(
            Files.isRegularFile(canonicalNote.toPath(), LinkOption.NOFOLLOW_LINKS)
        )
    }

    private fun resolveWorkspace(config: WorkspaceConfig, filesDir: File): Result<File> {
        val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
        if (workspaceResult.isFailure) {
            return Result.failure(
                NoteWriteException(NoteWriteError.InvalidWorkspacePath)
            )
        }

        val workspaceDir = workspaceResult.getOrThrow()
        if (!workspaceDir.isDirectory) {
            return Result.failure(
                NoteWriteException(NoteWriteError.WorkspaceMissing)
            )
        }

        return Result.success(workspaceDir)
    }
}

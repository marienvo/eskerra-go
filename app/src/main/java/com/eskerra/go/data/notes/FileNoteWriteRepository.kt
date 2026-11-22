package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.DatedNoteReadStatusRepository
import com.eskerra.go.core.repository.NoteWriteRepository
import com.eskerra.go.core.todayhub.DatedNoteReadStatus
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.git.WorkspaceGitRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileNoteWriteRepository(
    private val gitRepository: WorkspaceGitRepository,
    private val syncMutex: GitSyncMutex = GitSyncMutex()
) : NoteWriteRepository,
    DatedNoteReadStatusRepository {

    override suspend fun setRead(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        read: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }

        val result = syncMutex.mutex.withLock {
            val canonicalNote = try {
                File(workspaceDir, notePath.value).canonicalFile
            } catch (error: Exception) {
                return@withLock Result.failure(
                    NoteWriteException(NoteWriteError.WriteFailed(error.message))
                )
            }
            if (!FileNoteContentRepository.isStrictlyInsideWorkspace(
                    workspaceDir.canonicalFile,
                    canonicalNote
                )
            ) {
                return@withLock Result.failure(
                    NoteWriteException(NoteWriteError.InvalidNotePath)
                )
            }
            if (!Files.isRegularFile(canonicalNote.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return@withLock Result.failure(
                    NoteWriteException(NoteWriteError.WriteFailed("Dated note not found"))
                )
            }
            val current = try {
                canonicalNote.readText(Charsets.UTF_8)
            } catch (error: Exception) {
                return@withLock Result.failure(
                    NoteWriteException(NoteWriteError.WriteFailed(error.message))
                )
            }
            val updated = DatedNoteReadStatus.setRead(current, read)
                ?: return@withLock Result.failure(
                    NoteWriteException(NoteWriteError.WriteFailed("Dated note has no H1 title"))
                )
            if (updated == current) {
                Result.success(current)
            } else {
                gitRepository.writeFile(workspaceDir, notePath.value, updated).map { updated }
            }
        }
        result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    if (error is NoteWriteException) {
                        error
                    } else {
                        NoteWriteException(NoteWriteError.WriteFailed(error.message))
                    }
                )
            }
        )
    }

    override suspend fun listSiblingNames(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Set<String>> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }
        val parent = File(workspaceDir, notePath.value).parentFile
            ?: return@withContext Result.failure(
                NoteWriteException(NoteWriteError.InvalidNotePath)
            )
        val canonicalParent = try {
            parent.canonicalFile
        } catch (error: Exception) {
            return@withContext Result.failure(
                NoteWriteException(NoteWriteError.WriteFailed(error.message))
            )
        }
        val canonicalWorkspace = workspaceDir.canonicalFile
        val insideWorkspace = FileNoteContentRepository.isStrictlyInsideWorkspace(
            canonicalWorkspace,
            canonicalParent
        )
        if (!insideWorkspace) {
            return@withContext Result.failure(
                NoteWriteException(NoteWriteError.InvalidNotePath)
            )
        }
        val names = canonicalParent.listFiles()?.mapTo(linkedSetOf()) { it.name } ?: emptySet()
        Result.success(names)
    }

    override suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        markdown: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }

        val result = syncMutex.mutex.withLock {
            gitRepository.writeFile(workspaceDir, notePath.value, markdown)
        }
        result.fold(
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

    override suspend fun delete(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val workspaceDir = resolveWorkspace(config, filesDir).getOrElse {
            return@withContext Result.failure(it)
        }

        syncMutex.mutex.withLock { gitRepository.deleteFile(workspaceDir, notePath.value) }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                Result.failure(
                    NoteWriteException(NoteWriteError.DeleteFailed(error.message))
                )
            }
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

package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteContentError
import com.eskerra.go.core.model.NoteContentException
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileNoteContentRepository : NoteContentRepository {

    override suspend fun load(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId
    ): Result<NoteContent> = withContext(Dispatchers.IO) {
        val notePath = NotePath.fromRelativePath(noteId.value).getOrElse {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.InvalidNoteId)
            )
        }

        val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
        if (workspaceResult.isFailure) {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.InvalidWorkspacePath)
            )
        }

        val workspaceDir = workspaceResult.getOrThrow()
        if (!workspaceDir.isDirectory) {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.WorkspaceMissing)
            )
        }

        val noteFile = File(workspaceDir, notePath.value)
        val canonicalWorkspace = workspaceDir.canonicalFile
        val canonicalNote = try {
            noteFile.canonicalFile
        } catch (error: Exception) {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.ReadFailed(error.message))
            )
        }

        if (!isStrictlyInsideWorkspace(canonicalWorkspace, canonicalNote)) {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.InvalidNoteId)
            )
        }

        if (!Files.isRegularFile(canonicalNote.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return@withContext Result.failure(
                NoteContentException(NoteContentError.NotFound)
            )
        }

        try {
            val markdown = canonicalNote.readText(Charsets.UTF_8)
            Result.success(NoteContent(noteId, notePath, markdown))
        } catch (error: Exception) {
            Result.failure(NoteContentException(NoteContentError.ReadFailed(error.message)))
        }
    }

    internal companion object {
        fun isStrictlyInsideWorkspace(workspaceRoot: File, target: File): Boolean {
            val rootPath = workspaceRoot.canonicalFile.path
            val targetPath = target.canonicalFile.path
            if (targetPath == rootPath) {
                return false
            }
            return targetPath.startsWith(rootPath + File.separator)
        }
    }
}

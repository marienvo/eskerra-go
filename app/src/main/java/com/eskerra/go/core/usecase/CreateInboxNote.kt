package com.eskerra.go.core.usecase

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.CreateInboxNoteResult
import com.eskerra.go.core.model.CreateNoteError
import com.eskerra.go.core.model.CreateNoteException
import com.eskerra.go.core.model.EditableNote
import com.eskerra.go.core.model.InboxNoteDraft
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.NoteWriteRepository
import java.io.File

/** Creates a new inbox markdown note from compose draft text, refreshes registry, returns Git status. */
class CreateInboxNote(
    private val writeRepository: NoteWriteRepository,
    private val registryRepository: NoteRegistryRepository,
    private val loadGitStatusSummary: LoadGitStatusSummary,
    private val maxCollisionAttempts: Int = 100
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        draft: String
    ): Result<CreateInboxNoteResult> {
        if (!InboxNoteDraft.hasNonBlankTitle(draft)) {
            return Result.failure(
                CreateNoteException(CreateNoteError.WriteFailed("Title must not be blank"))
            )
        }

        val markdown = InboxNoteDraft.toMarkdown(draft)
        val stem = InboxNoteDraft.toFilenameStem(InboxNoteDraft.extractTitleLine(draft))
        val notePath = allocateInboxPathFromStem(config, filesDir, stem)
            .getOrElse { return Result.failure(it) }

        writeRepository.write(config, filesDir, notePath, markdown).getOrElse { error ->
            return Result.failure(mapWriteFailure(error))
        }

        val registryResult = registryRepository.refresh(config, filesDir)
        if (registryResult.isFailure) {
            return Result.failure(
                CreateNoteException(
                    CreateNoteError.RegistryRefreshFailed(
                        registryResult.exceptionOrNull()?.message
                    )
                )
            )
        }

        val summary = registryResult.getOrThrow().notes.find { it.id == NoteId(notePath.value) }
            ?: return Result.failure(
                CreateNoteException(
                    CreateNoteError.VerificationFailed("Created note missing from registry")
                )
            )

        val gitStatus = loadGitStatusSummary(config, filesDir)
        return Result.success(
            CreateInboxNoteResult(
                note = EditableNote(
                    id = summary.id,
                    path = notePath,
                    title = summary.title,
                    markdown = markdown,
                    isInbox = summary.isInbox,
                    canEdit = summary.isInbox
                ),
                gitStatus = gitStatus
            )
        )
    }

    private suspend fun allocateInboxPathFromStem(
        config: WorkspaceConfig,
        filesDir: File,
        stem: String
    ): Result<NotePath> {
        val basePath = "$INBOX_PREFIX$stem$MARKDOWN_SUFFIX"

        for (attempt in 1..maxCollisionAttempts) {
            val candidatePath = if (attempt == 1) {
                basePath
            } else {
                "$INBOX_PREFIX$stem-$attempt$MARKDOWN_SUFFIX"
            }

            val notePath = NotePath.fromRelativePath(candidatePath).getOrElse {
                return Result.failure(
                    CreateNoteException(CreateNoteError.WriteFailed(it.message))
                )
            }

            if (!notePath.value.startsWith(INBOX_PREFIX)) {
                return Result.failure(
                    CreateNoteException(CreateNoteError.WriteFailed("Path must start with Inbox/"))
                )
            }

            val exists = writeRepository.exists(config, filesDir, notePath).getOrElse { error ->
                return Result.failure(mapWriteFailure(error))
            }

            if (!exists) {
                return Result.success(notePath)
            }
        }

        return Result.failure(
            CreateNoteException(
                CreateNoteError.WriteFailed("Could not allocate a unique inbox filename")
            )
        )
    }

    internal companion object {
        const val INBOX_PREFIX = "${InboxNotePath.INBOX_DIRECTORY}/"
        const val MARKDOWN_SUFFIX = ".md"

        private fun mapWriteFailure(error: Throwable): CreateNoteException {
            if (error is NoteWriteException) {
                val mapped = when (error.error) {
                    NoteWriteError.InvalidWorkspacePath ->
                        CreateNoteError.InvalidWorkspacePath
                    NoteWriteError.WorkspaceMissing ->
                        CreateNoteError.WorkspaceMissing
                    NoteWriteError.InvalidNotePath,
                    is NoteWriteError.WriteFailed,
                    is NoteWriteError.DeleteFailed ->
                        CreateNoteError.WriteFailed(
                            (error.error as? NoteWriteError.WriteFailed)?.detail
                                ?: (error.error as? NoteWriteError.DeleteFailed)?.detail
                        )
                }
                return CreateNoteException(mapped)
            }
            return CreateNoteException(CreateNoteError.WriteFailed(error.message))
        }
    }
}

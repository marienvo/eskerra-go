package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.CreateInboxNoteResult
import com.eskerra.go.core.model.CreateNoteError
import com.eskerra.go.core.model.CreateNoteException
import com.eskerra.go.core.model.EditableNote
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteWriteError
import com.eskerra.go.core.model.NoteWriteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.NoteRegistryRepository
import com.eskerra.go.core.repository.NoteWriteRepository
import com.eskerra.go.data.notes.MarkdownNoteScanner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Creates a new inbox markdown note, refreshes the registry, and returns Git status. */
class CreateInboxNote(
    private val writeRepository: NoteWriteRepository,
    private val registryRepository: NoteRegistryRepository,
    private val loadGitStatusSummary: LoadGitStatusSummary,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val maxCollisionAttempts: Int = 100
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        title: String? = null,
        initialMarkdown: String? = null
    ): Result<CreateInboxNoteResult> {
        val instant = clock.instant()
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultTitle(instant, clock.zone)
        val markdown = initialMarkdown?.takeIf { it.isNotBlank() }
            ?: defaultMarkdown(resolvedTitle)

        val notePath = allocateInboxPath(config, filesDir, instant)
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

    private suspend fun allocateInboxPath(
        config: WorkspaceConfig,
        filesDir: File,
        instant: Instant
    ): Result<NotePath> {
        val baseName = FILENAME_FORMATTER.format(instant.atZone(clock.zone))
        val basePath = "$INBOX_PREFIX$baseName$MARKDOWN_SUFFIX"

        for (attempt in 1..maxCollisionAttempts) {
            val candidatePath = if (attempt == 1) {
                basePath
            } else {
                "$INBOX_PREFIX$baseName-$attempt$MARKDOWN_SUFFIX"
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
        const val INBOX_PREFIX = "${MarkdownNoteScanner.INBOX_DIRECTORY}/"
        const val MARKDOWN_SUFFIX = ".md"
        const val DEFAULT_TITLE = "Untitled inbox note"

        private val FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        fun defaultTitle(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String {
            val formatted = DISPLAY_TITLE_FORMATTER.format(instant.atZone(zoneId))
            return "Inbox note $formatted"
        }

        fun defaultMarkdown(title: String): String = "# $title\n\n"

        private val DISPLAY_TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        private fun mapWriteFailure(error: Throwable): CreateNoteException {
            if (error is NoteWriteException) {
                val mapped = when (error.error) {
                    NoteWriteError.InvalidWorkspacePath ->
                        CreateNoteError.InvalidWorkspacePath
                    NoteWriteError.WorkspaceMissing ->
                        CreateNoteError.WorkspaceMissing
                    NoteWriteError.InvalidNotePath,
                    is NoteWriteError.WriteFailed ->
                        CreateNoteError.WriteFailed(
                            (error.error as? NoteWriteError.WriteFailed)?.detail
                        )
                }
                return CreateNoteException(mapped)
            }
            return CreateNoteException(CreateNoteError.WriteFailed(error.message))
        }
    }
}

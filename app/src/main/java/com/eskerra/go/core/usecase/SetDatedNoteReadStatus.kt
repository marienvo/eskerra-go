package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.SaveNoteError
import com.eskerra.go.core.model.SaveNoteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.DatedNoteReadStatusRepository
import com.eskerra.go.core.repository.NoteContentCachePort
import com.eskerra.go.core.repository.NoteRegistryCachePort
import com.eskerra.go.core.todayhub.DatedNoteReadStatus
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.todayhub.TodayHubWeeks
import com.eskerra.go.core.vault.VaultVisibility
import java.io.File

data class DatedNoteReadStatusResult(
    val markdown: String,
    val registry: NoteRegistry,
    val registryRefreshFailed: Boolean
)

/** Changes only the read marker on an existing dated note, then refreshes derived note state. */
class SetDatedNoteReadStatus(
    private val repository: DatedNoteReadStatusRepository,
    private val registryCache: NoteRegistryCachePort,
    private val contentCache: NoteContentCachePort? = null
) {
    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        noteId: NoteId,
        read: Boolean
    ): Result<DatedNoteReadStatusResult> {
        val notePath = NotePath.fromRelativePath(noteId.value).getOrElse {
            return Result.failure(SaveNoteException(SaveNoteError.InvalidNoteId))
        }
        if (!isDatedNote(noteId)) {
            return Result.failure(SaveNoteException(SaveNoteError.ReadOnlyNote))
        }

        val registry = registryCache.current(config, filesDir) ?: registryCache.refresh(
            config,
            filesDir
        ).getOrElse {
            return Result.failure(it)
        }
        val summary = registry.notes.find { it.id == noteId }
            ?: return Result.failure(SaveNoteException(SaveNoteError.NotFound))

        val markdown = repository.setRead(config, filesDir, notePath, read).getOrElse {
            return Result.failure(it)
        }
        contentCache?.evict(noteId)

        val refreshed = registryCache.refresh(config, filesDir)
        val refreshedRegistry = refreshed.getOrElse { registry }
        val title = DatedNoteReadStatus.title(markdown) ?: summary.title
        val updatedRegistry = NoteRegistry.fromNotes(
            refreshedRegistry.notes.map { note ->
                if (note.id == noteId) note.copy(title = title) else note
            }
        )
        return Result.success(
            DatedNoteReadStatusResult(
                markdown = markdown,
                registry = updatedRegistry,
                registryRefreshFailed = refreshed.isFailure
            )
        )
    }

    private fun isDatedNote(noteId: NoteId): Boolean {
        val folderPath = TodayHubDiscovery.directoryOf(noteId.value)
        val folder = folderPath.substringAfterLast('/')
        val fileName = noteId.value.substringAfterLast('/')
        if (folderPath.isEmpty() ||
            folder == "Inbox" ||
            VaultVisibility.isExcludedDirectorySegment(folder)
        ) {
            return false
        }
        return fileName.endsWith(".md") &&
            TodayHubWeeks.parseRowStem(fileName.removeSuffix(".md")) != null
    }
}

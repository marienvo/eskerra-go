package com.eskerra.go.core.usecase

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnoozeRepository
import com.eskerra.go.core.repository.NoteContentCachePort
import com.eskerra.go.core.repository.NoteRegistryCachePort
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Reports each completed write, even when another note fails. */
class SnoozeInboxNotes(
    private val repository: InboxSnoozeRepository,
    private val registry: NoteRegistryCachePort,
    private val contentCache: NoteContentCachePort
) {
    data class Outcome(
        val succeeded: List<NoteId>,
        val failed: List<NoteId>,
        val refreshFailed: Boolean
    )

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        ids: List<NoteId>,
        available: List<NoteSummary>,
        until: Instant
    ): Outcome {
        val succeeded = mutableListOf<NoteId>()
        val failed = mutableListOf<NoteId>()
        for (id in ids.distinct()) {
            val note = available.singleOrNull { it.id == id && it.isInbox }
            if (note == null || !InboxNotePath.isInboxRelativePath(id.value)
            ) {
                failed += id
                continue
            }
            if (repository.snooze(config, filesDir, note, until).isSuccess) {
                succeeded += id
                contentCache.evict(id)
            } else {
                failed += id
            }
        }
        val refreshFailed = if (succeeded.isEmpty()) {
            false
        } else {
            try {
                registry.refreshAffected(config, filesDir, succeeded.toSet()).isFailure
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                true
            }
        }
        return Outcome(succeeded, failed, refreshFailed)
    }
}

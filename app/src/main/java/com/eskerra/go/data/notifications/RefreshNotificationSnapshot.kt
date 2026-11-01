package com.eskerra.go.data.notifications

import com.eskerra.go.core.inbox.InboxSnoozeFrontmatter
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.DatedNoteNotificationCandidates
import com.eskerra.go.core.notifications.InboxNotificationCandidates
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.ReminderCandidates
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteflowRepository
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Builds a complete snapshot off the UI thread and publishes it only after every read succeeds. */
class RefreshNotificationSnapshot(
    private val notes: NoteContentRepository,
    private val noteflows: NoteflowRepository,
    private val store: NotificationSnapshotPersistence,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        registry: NoteRegistry
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val at = now()
            val workspace = notificationWorkspaceKey(config)
            val localZone = zone()
            val day = java.time.Instant.ofEpochMilli(at).atZone(localZone).toLocalDate()
            val today = LocalDate(day.year, day.monthValue, day.dayOfMonth)
            val markers = noteflows.discover(config, filesDir).getOrThrow().map { it.id }
            val datedIds = markers.flatMap { marker ->
                NoteflowDatedNotes.select(registry, marker, today).map { it.noteId }
            }.toSet()
            val content = mutableMapOf<NoteId, String>()
            val events = mutableListOf<NotificationEvent>()
            val inbox = mutableListOf<NoteSummary>()
            for (note in registry.notes) {
                val markdown = notes.load(config, filesDir, note.id).getOrThrow().markdown
                if (note.id in datedIds) content[note.id] = markdown
                if (note.isInbox) {
                    val deadline = InboxSnoozeFrontmatter.read(markdown)?.toEpochMilli() ?: 0L
                    inbox += note.copy(snoozedUntilEpochMillis = deadline)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(markdown.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                val signature = "v2:${localZone.id}:$digest"
                val cached = store.cachedReminders(workspace, note.id.value, signature)
                if (cached == null) {
                    val reminders = ReminderCandidates.scan(note.id.value, markdown, localZone)
                    store.cacheReminders(workspace, note.id.value, signature, reminders)
                    events.addAll(reminders)
                } else {
                    events.addAll(cached)
                }
            }
            events += InboxNotificationCandidates.fromRegistry(NoteRegistry(inbox))
            events += DatedNoteNotificationCandidates.select(registry, markers, today, content)
            store.replaceSnapshot(workspace, events, at)
        }.onFailure { if (it is CancellationException) throw it }
    }
}

internal fun notificationWorkspaceKey(config: WorkspaceConfig): String =
    "${config.relativePath}:${config.setupCompletedAtEpochMs}"

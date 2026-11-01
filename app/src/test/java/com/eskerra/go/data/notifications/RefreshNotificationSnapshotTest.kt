package com.eskerra.go.data.notifications

import com.eskerra.go.core.model.NoteContent
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.repository.NoteContentRepository
import com.eskerra.go.core.repository.NoteflowRepository
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshNotificationSnapshotTest {
    private val config = WorkspaceConfig("vault", "vault", "https://example.com", "main", 1)
    private val id = NoteId("Inbox/a.md")
    private val registry = NoteRegistry.fromNotes(listOf(NoteSummary(id, "A", "", true)))

    @Test
    fun reusesCachedRemindersAndPublishesCompleteSnapshots() = runTest {
        val store = FakeStore()
        var reads = 0
        val notes = object : NoteContentRepository {
            override suspend fun load(
                config: WorkspaceConfig,
                filesDir: File,
                noteId: NoteId
            ): Result<NoteContent> {
                reads++
                return Result.success(
                    NoteContent(
                        noteId,
                        NotePath.fromRelativePath(noteId.value).getOrThrow(),
                        "@2026-11-01_1000"
                    )
                )
            }
        }
        val refresh =
            RefreshNotificationSnapshot(
                notes,
                NoteflowRepository { _, _ ->
                    Result.success(emptyList())
                },
                store,
                { 1L },
                { ZoneId.of("UTC") }
            )
        assertTrue(refresh.refresh(config, File("."), registry).isSuccess)
        assertTrue(refresh.refresh(config, File("."), registry).isSuccess)
        assertEquals(2, reads)
        assertEquals(2, store.published.size)
        assertEquals(2, store.published.last().size)
    }

    @Test
    fun changedContentWithSameMetadataRefreshesSnoozeAndReminders() = runTest {
        val store = FakeStore()
        var body = "---\nsnoozed_until: \"2026-11-02T08:00:00Z\"\n---\n@2026-11-02_0900"
        val notes = object : NoteContentRepository {
            override suspend fun load(
                config: WorkspaceConfig,
                filesDir: File,
                noteId: NoteId
            ): Result<NoteContent> = Result.success(
                NoteContent(noteId, NotePath.fromRelativePath(noteId.value).getOrThrow(), body)
            )
        }
        val refresh =
            RefreshNotificationSnapshot(
                notes,
                NoteflowRepository { _, _ ->
                    Result.success(emptyList())
                },
                store,
                { 1L },
                { ZoneId.of("UTC") }
            )
        assertTrue(refresh.refresh(config, File("."), registry).isSuccess)
        val before = store.published.last()
        body = body.replace("08:00", "09:00").replace("_0900", "_1000")
        assertTrue(refresh.refresh(config, File("."), registry).isSuccess)
        val after = store.published.last()
        assertTrue(
            before.single {
                it.type ==
                    com.eskerra.go.core.notifications.NotificationEventType.SNOOZE
            }.key !=
                after.single {
                    it.type ==
                        com.eskerra.go.core.notifications.NotificationEventType.SNOOZE
                }.key
        )
        assertTrue(
            before.single {
                it.type ==
                    com.eskerra.go.core.notifications.NotificationEventType.REMINDER
            }.key !=
                after.single {
                    it.type ==
                        com.eskerra.go.core.notifications.NotificationEventType.REMINDER
                }.key
        )
    }

    @Test
    fun resettingWorkspaceChangesSnapshotIdentity() {
        assertTrue(
            notificationWorkspaceKey(config) !=
                notificationWorkspaceKey(config.copy(setupCompletedAtEpochMs = 2))
        )
    }

    @Test
    fun failedReadDoesNotReplaceLastCompleteSnapshot() = runTest {
        val store = FakeStore()
        val notes = object : NoteContentRepository {
            override suspend fun load(
                config: WorkspaceConfig,
                filesDir: File,
                noteId: NoteId
            ): Result<NoteContent> = Result.failure(IllegalStateException("read failed"))
        }
        val refresh =
            RefreshNotificationSnapshot(
                notes,
                NoteflowRepository { _, _ ->
                    Result.success(emptyList())
                },
                store
            )
        assertTrue(refresh.refresh(config, File("."), registry).isFailure)
        assertTrue(store.published.isEmpty())
    }

    private class FakeStore : NotificationSnapshotPersistence {
        private val cached = mutableMapOf<String, List<NotificationEvent>>()
        val published = mutableListOf<List<NotificationEvent>>()
        override fun cachedReminders(workspace: String, noteId: String, signature: String) =
            cached["$workspace/$noteId/$signature"]
        override fun cacheReminders(
            workspace: String,
            noteId: String,
            signature: String,
            events: List<NotificationEvent>
        ) {
            cached["$workspace/$noteId/$signature"] = events
        }
        override fun replaceSnapshot(
            workspace: String,
            events: List<NotificationEvent>,
            now: Long
        ) {
            published += events
        }
    }
}

package com.eskerra.go.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
import com.eskerra.go.core.notifications.ReminderCandidates
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSnapshotStoreTest {
    @Test
    fun baselineLocalChangesAndPruningSurviveReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val local =
            NotificationEvent(
                "inbox:Inbox/local.md",
                "Inbox/local.md",
                NotificationEventType.INBOX,
                0
            )
        val remote =
            NotificationEvent(
                "inbox:Inbox/remote.md",
                "Inbox/remote.md",
                NotificationEventType.INBOX,
                0
            )
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, emptyList(), 100)
            store.rememberLocalInboxPaths(workspace, setOf(local.noteId))
            store.replaceSnapshot(workspace, listOf(local, remote), 200)
            assertEquals(listOf(remote), store.pending(workspace))
        }
        NotificationSnapshotStore(context).use { reopened ->
            assertEquals(listOf(remote), reopened.pending(workspace))
            reopened.replaceSnapshot(workspace, listOf(local), 300)
            assertEquals(emptyList<NotificationEvent>(), reopened.pending(workspace))
        }
    }

    @Test
    fun futureReminderIsQueuedBeforeItsDeadline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val event = ReminderCandidates.scan(
            "Personal/2026-11-01.md",
            "- 🐞 @2026-11-01_1520 Test mobiele reminder",
            ZoneId.of("UTC")
        ).single()
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, listOf(event), event.dueAtEpochMillis - 1)
            assertEquals(listOf(event), store.pending(workspace))
        }
    }

    @Test
    fun pendingReminderSurvivesAContextEdit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val old = ReminderCandidates.scan(
            "a.md",
            "Before @2026-11-01_1000",
            ZoneId.of("UTC")
        ).single()
        val updated = ReminderCandidates.scan(
            "a.md",
            "After @2026-11-01_1000",
            ZoneId.of("UTC")
        ).single()
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, listOf(old), old.dueAtEpochMillis - 100)
            store.replaceSnapshot(workspace, listOf(old), old.dueAtEpochMillis)
            assertEquals(listOf(old), store.pending(workspace))
            store.replaceSnapshot(workspace, listOf(updated), old.dueAtEpochMillis + 100)
            assertEquals(listOf(updated), store.pending(workspace))
        }
    }

    @Test
    fun timezoneChangeRearmsReminderThatIsNowInTheFuture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val old = ReminderCandidates.scan("a.md", "@2026-11-01_1000", ZoneId.of("UTC")).single()
        val later = ReminderCandidates.scan(
            "a.md",
            "@2026-11-01_1000",
            ZoneId.of("America/New_York")
        ).single()
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, listOf(old), old.dueAtEpochMillis - 1)
            store.replaceSnapshot(workspace, listOf(old), old.dueAtEpochMillis)
            assertEquals(listOf(old), store.pending(workspace))
            store.replaceSnapshot(workspace, listOf(later), old.dueAtEpochMillis + 1)
            assertEquals(emptyList<NotificationEvent>(), store.pending(workspace))
            store.replaceSnapshot(workspace, listOf(later), later.dueAtEpochMillis)
            assertEquals(listOf(later), store.pending(workspace))
        }
    }

    @Test
    fun acknowledgementDoesNotDeleteChangedDeadline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val event = ReminderCandidates.scan(
            "a.md",
            "@2026-11-01_1000",
            ZoneId.of("UTC")
        ).single()
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, listOf(event), event.dueAtEpochMillis - 1)
            store.replaceSnapshot(workspace, listOf(event), event.dueAtEpochMillis)
            store.acknowledge(workspace, event.key, event.dueAtEpochMillis - 1)
            assertEquals(listOf(event), store.pending(workspace))
            store.acknowledge(workspace, event.key, event.dueAtEpochMillis)
            assertEquals(emptyList<NotificationEvent>(), store.pending(workspace))
        }
    }

    @Test
    fun ambiguousDuplicateKeepsOnePendingNotificationForTheSameNote() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = "test-${UUID.randomUUID()}"
        val old = ReminderCandidates.scan(
            "a.md",
            "Same @2026-11-01\nSame @2026-11-01",
            ZoneId.of("UTC")
        )
        val current = ReminderCandidates.scan("a.md", "Same @2026-11-01", ZoneId.of("UTC"))
        val due = old.first().dueAtEpochMillis
        NotificationSnapshotStore(context).use { store ->
            store.replaceSnapshot(workspace, old, due - 1)
            store.replaceSnapshot(workspace, old, due)
            assertEquals(2, store.pending(workspace).size)
            store.replaceSnapshot(workspace, current, due + 1)
            assertEquals(current, store.pending(workspace))
        }
    }
}

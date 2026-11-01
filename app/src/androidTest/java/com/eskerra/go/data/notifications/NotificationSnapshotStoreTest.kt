package com.eskerra.go.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.notifications.NotificationEvent
import com.eskerra.go.core.notifications.NotificationEventType
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
}

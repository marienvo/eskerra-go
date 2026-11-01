package com.eskerra.go.core.notifications

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxNotificationCandidatesTest {
    @Test
    fun inboxAndSnoozedItemsHaveDistinctStableOccurrences() {
        val registry = NoteRegistry.fromNotes(
            listOf(
                NoteSummary(NoteId("Inbox/a.md"), "A", "", true),
                NoteSummary(NoteId("Inbox/b.md"), "B", "", true, snoozedUntilEpochMillis = 123),
                NoteSummary(NoteId("Other/c.md"), "C", "", false)
            )
        )
        assertEquals(
            setOf("inbox:Inbox/a.md", "inbox:Inbox/b.md", "snooze:Inbox/b.md:123"),
            InboxNotificationCandidates.fromRegistry(registry).map { it.key }.toSet()
        )
        assertEquals(
            123L,
            InboxNotificationCandidates.fromRegistry(registry).single {
                it.type == NotificationEventType.SNOOZE
            }.dueAtEpochMillis
        )
    }

    @Test
    fun clearingAnExistingSnoozeDoesNotCreateANewInboxEvent() {
        val id = NoteId("Inbox/b.md")
        val snoozed = NoteRegistry.fromNotes(
            listOf(NoteSummary(id, "B", "", true, snoozedUntilEpochMillis = 500))
        )
        val cleared = NoteRegistry.fromNotes(listOf(NoteSummary(id, "B", "", true)))
        val previous = InboxNotificationCandidates.fromRegistry(snoozed).associateBy { it.key }
        val events = NotificationEvents.newlyDue(
            previous,
            100,
            InboxNotificationCandidates.fromRegistry(cleared),
            200
        )
        assertEquals(emptyList<NotificationEvent>(), events)
    }
}

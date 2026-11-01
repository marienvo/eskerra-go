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
            setOf("inbox:Inbox/a.md", "snooze:Inbox/b.md:123"),
            InboxNotificationCandidates.fromRegistry(registry).map { it.key }.toSet()
        )
        assertEquals(
            123L,
            InboxNotificationCandidates.fromRegistry(registry).single {
                it.type == NotificationEventType.SNOOZE
            }.dueAtEpochMillis
        )
    }
}

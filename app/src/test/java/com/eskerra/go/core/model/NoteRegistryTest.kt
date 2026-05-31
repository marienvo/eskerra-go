package com.eskerra.go.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRegistryTest {

    @Test
    fun inboxSummaries_sortByLastModifiedDescendingWithPathTieBreaker() {
        val older = NoteSummary(
            id = NoteId("Inbox/a.md"),
            title = "A",
            snippet = "",
            isInbox = true,
            lastModifiedEpochMillis = 100L
        )
        val newer = NoteSummary(
            id = NoteId("Inbox/b.md"),
            title = "B",
            snippet = "",
            isInbox = true,
            lastModifiedEpochMillis = 200L
        )
        val tiedFirst = NoteSummary(
            id = NoteId("Inbox/aaa.md"),
            title = "AAA",
            snippet = "",
            isInbox = true,
            lastModifiedEpochMillis = 200L
        )
        val tiedSecond = NoteSummary(
            id = NoteId("Inbox/bbb.md"),
            title = "BBB",
            snippet = "",
            isInbox = true,
            lastModifiedEpochMillis = 200L
        )
        val nonInbox = NoteSummary(
            id = NoteId("Notes/outside.md"),
            title = "Outside",
            snippet = "",
            isInbox = false,
            lastModifiedEpochMillis = 999L
        )

        val registry = NoteRegistry.fromNotes(listOf(older, newer, tiedSecond, tiedFirst, nonInbox))

        assertEquals(
            listOf("Inbox/aaa.md", "Inbox/b.md", "Inbox/bbb.md", "Inbox/a.md"),
            registry.inboxSummaries.map { it.id.value }
        )
    }

    @Test
    fun fromNotes_keepsPathSortForFullRegistry() {
        val notes = listOf(
            note("Inbox/z.md"),
            note("Inbox/a.md")
        )
        val registry = NoteRegistry.fromNotes(notes)

        assertEquals(listOf("Inbox/a.md", "Inbox/z.md"), registry.notes.map { it.id.value })
    }

    private fun note(path: String): NoteSummary = NoteSummary(
        id = NoteId(path),
        title = path,
        snippet = "",
        isInbox = path.startsWith("Inbox/")
    )
}

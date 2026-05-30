package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary

/**
 * Hardcoded note content for the UI-only step. This is the single, explicit
 * source of fake notes. There is no filesystem, database, or Git behind it.
 */
object FakeNotes {

    /** A fake note: a summary plus a markdown-ish body that may contain `[[wiki links]]`. */
    data class FakeNote(
        val summary: NoteSummary,
        val body: String,
    )

    private val notes: List<FakeNote> = listOf(
        FakeNote(
            summary = NoteSummary(
                id = NoteId("note-welcome"),
                title = "Welcome to Eskerra Go",
                snippet = "A quick tour of the inbox-first workflow.",
                isInbox = true,
            ),
            body = """
                # Welcome

                This is a fake note used to prove the UI shape.

                Try opening [[Reading List]] or [[Meeting Notes]] from here.
                Tapping a wiki link should navigate to that note.
            """.trimIndent(),
        ),
        FakeNote(
            summary = NoteSummary(
                id = NoteId("note-reading-list"),
                title = "Reading List",
                snippet = "Books and articles to get to eventually.",
                isInbox = true,
            ),
            body = """
                # Reading List

                - Compose internals
                - Material 3 guidelines

                Back to [[Welcome to Eskerra Go]] when you are done.
            """.trimIndent(),
        ),
        FakeNote(
            summary = NoteSummary(
                id = NoteId("note-meeting"),
                title = "Meeting Notes",
                snippet = "Sync notes from the weekly planning call.",
                isInbox = true,
            ),
            body = """
                # Meeting Notes

                Action items captured during planning.

                Related: [[Reading List]].
            """.trimIndent(),
        ),
    )

    fun inboxSummaries(): List<NoteSummary> =
        notes.map { it.summary }.filter { it.isInbox }

    fun note(id: NoteId): FakeNote? =
        notes.firstOrNull { it.summary.id == id }

    /** Resolves a wiki link [target] (by title) to a note id, if one exists. */
    fun resolveWikiLink(target: String): NoteId? =
        notes.firstOrNull { it.summary.title.equals(target, ignoreCase = true) }?.summary?.id
}

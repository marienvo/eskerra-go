package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnoozeRepository
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeInboxNotesTest {
    @Test
    fun reportsPartialSuccessAndRejectsNonInboxEntries() = runBlocking {
        val calls = mutableListOf<NoteId>()
        val repository = object : InboxSnoozeRepository {
            override suspend fun snooze(
                config: WorkspaceConfig,
                filesDir: File,
                noteId: NoteId,
                until: Instant
            ): Result<Unit> {
                calls += noteId
                return if (noteId.value.endsWith("bad.md")) {
                    Result.failure(IllegalStateException())
                } else {
                    Result.success(Unit)
                }
            }
        }
        val good = NoteId("Inbox/good.md")
        val bad = NoteId("Inbox/bad.md")
        val outside = NoteId("General/other.md")
        val available = listOf(good, bad, outside).map { NoteSummary(it, "", "", true) }
        val usecase = SnoozeInboxNotes(
            repository,
            NoteRegistryCache(FakeNoteRegistryRepository()),
            NoteContentCache(FakeNoteContentRepository())
        )
        val outcome = usecase(
            WorkspaceConfig("Test", "vault", null, "main", 1),
            File("."),
            listOf(good, bad, outside),
            available,
            Instant.parse("2026-11-02T08:00:00Z")
        )
        assertEquals(listOf(good), outcome.succeeded)
        assertEquals(listOf(bad, outside), outcome.failed)
        assertEquals(listOf(good, bad), calls)
        assertEquals(false, outcome.refreshFailed)
    }
}

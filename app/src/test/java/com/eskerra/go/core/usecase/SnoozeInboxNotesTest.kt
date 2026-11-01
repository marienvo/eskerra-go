package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnoozeRepository
import com.eskerra.go.core.repository.NoteRegistryCachePort
import com.eskerra.go.data.notes.FakeNoteContentRepository
import com.eskerra.go.data.notes.FakeNoteRegistryRepository
import com.eskerra.go.data.notes.NoteContentCache
import com.eskerra.go.data.notes.NoteRegistryCache
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
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
                note: NoteSummary,
                until: Instant
            ): Result<Unit> {
                calls += note.id
                return if (note.id.value.endsWith("bad.md")) {
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

    @Test
    fun reportsCompletedWritesWhenRegistryRefreshThrows() = runBlocking {
        val registry = object : NoteRegistryCachePort {
            override val registry = MutableStateFlow<NoteRegistry?>(null)
            override suspend fun current(config: WorkspaceConfig, filesDir: File): NoteRegistry? =
                null
            override suspend fun refresh(
                config: WorkspaceConfig,
                filesDir: File
            ): Result<NoteRegistry> = throw IllegalStateException("scan failed")
            override suspend fun invalidate(config: WorkspaceConfig, filesDir: File) = Unit
        }
        val writer = object : InboxSnoozeRepository {
            override suspend fun snooze(
                config: WorkspaceConfig,
                filesDir: File,
                note: NoteSummary,
                until: Instant
            ): Result<Unit> = Result.success(Unit)
        }
        val note = NoteSummary(NoteId("Inbox/note.md"), "Note", "", true)
        val usecase = SnoozeInboxNotes(
            writer,
            registry,
            NoteContentCache(FakeNoteContentRepository())
        )
        val outcome = usecase(
            WorkspaceConfig("Test", "vault", null, "main", 1),
            File("."),
            listOf(note.id),
            listOf(note),
            Instant.parse("2026-11-02T08:00:00Z")
        )
        assertEquals(listOf(note.id), outcome.succeeded)
        assertEquals(true, outcome.refreshFailed)
    }
}

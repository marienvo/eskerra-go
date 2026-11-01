package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRegistryCacheAffectedTest {
    @Test
    fun changedNotesAreExcludedFromTheNextIncrementalScan() = runBlocking {
        val first = NoteSummary(NoteId("Inbox/first.md"), "First", "", true)
        val second = NoteSummary(NoteId("Inbox/second.md"), "Second", "", true)
        val repository = FakeNoteRegistryRepository.withInboxNotes(first, second)
        val cache = NoteRegistryCache(repository)
        val config = WorkspaceConfig("Test", "vault", null, "main", 1)
        cache.refresh(config, File(".")).getOrThrow()
        cache.refreshAffected(config, File("."), setOf(first.id)).getOrThrow()
        assertEquals(listOf(second), repository.lastPreviousRegistry?.notes)
        assertEquals(listOf(first, second), cache.registry.value?.notes)
    }
}

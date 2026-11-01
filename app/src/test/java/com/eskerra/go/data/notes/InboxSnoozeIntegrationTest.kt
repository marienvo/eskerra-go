package com.eskerra.go.data.notes

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.SnoozeInboxNotes
import com.eskerra.go.data.git.GitSyncMutex
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InboxSnoozeIntegrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writeRefreshesTheSelectedNoteInTheSharedRegistry() = runBlocking {
        val files = temp.newFolder()
        val inbox = File(files, "vault/Inbox").apply { mkdirs() }
        val note = File(inbox, "note.md").apply { writeText("---\ntags:\n  - work\n---\nBody\n") }
        val config = WorkspaceConfig("Test", "vault", null, "main", 1)
        val registry = NoteRegistryCache(
            CoalescingNoteRegistryRepository(FileNoteRegistryRepository())
        )
        val original = registry.refresh(config, files).getOrThrow().inboxSummaries.single()
        val operation = SnoozeInboxNotes(
            FileInboxSnoozeRepository(GitSyncMutex()),
            registry,
            NoteContentCache(FakeNoteContentRepository())
        )
        val until = Instant.parse("2026-11-02T08:00:00Z")
        val outcome = operation(config, files, listOf(original.id), listOf(original), until)
        assertEquals(listOf(original.id), outcome.succeeded)
        assertTrue(outcome.failed.isEmpty())
        assertEquals(
            until.toEpochMilli(),
            registry.registry.value?.inboxSummaries?.single()?.snoozedUntilEpochMillis
        )
        assertTrue(note.readText().contains("tags:\n  - work\nsnoozed_until:"))
    }
}

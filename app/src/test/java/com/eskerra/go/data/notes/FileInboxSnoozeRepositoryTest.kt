package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.GitSyncMutex
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileInboxSnoozeRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = WorkspaceConfig("Test", "vault", null, "main", 1L)
    private val until = Instant.parse("2026-11-02T08:00:00Z")

    @Test
    fun modifiesOnlyTheSelectedInboxNote() = runBlocking {
        val root = temp.newFolder()
        val inbox = File(root, "vault/Inbox").apply { mkdirs() }
        val note = File(inbox, "note.md").apply { writeText("Body\n") }
        val neighbor = File(inbox, "other.md").apply { writeText("Other\n") }
        val repo = FileInboxSnoozeRepository(GitSyncMutex())
        assertTrue(repo.snooze(config, root, NoteId("Inbox/note.md"), until).isSuccess)
        assertEquals("---\nsnoozed_until: \"$until\"\n---\nBody\n", note.readText())
        assertEquals("Other\n", neighbor.readText())
        assertTrue(repo.snooze(config, root, NoteId("General/note.md"), until).isFailure)
    }

    @Test
    fun unsafeFrontmatterLeavesFileUnchanged() = runBlocking {
        val root = temp.newFolder()
        val inbox = File(root, "vault/Inbox").apply { mkdirs() }
        val original = "---\ntags: [broken\n---\nBody"
        val note = File(inbox, "note.md").apply { writeText(original) }
        val repo = FileInboxSnoozeRepository(GitSyncMutex())
        assertTrue(repo.snooze(config, root, NoteId("Inbox/note.md"), until).isFailure)
        assertEquals(original, note.readText())
    }
}

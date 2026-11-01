package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.data.git.GitSyncMutex
import java.io.File
import java.nio.file.Files
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
        assertTrue(repo.snooze(config, root, summary(note), until).isSuccess)
        assertEquals("---\nsnoozed_until: \"$until\"\n---\nBody\n", note.readText())
        assertEquals("Other\n", neighbor.readText())
        assertTrue(
            repo.snooze(
                config,
                root,
                summary(note).copy(id = NoteId("General/note.md")),
                until
            ).isFailure
        )
    }

    @Test
    fun rejectsAChangedNoteAfterSelection() = runBlocking {
        val root = temp.newFolder()
        val inbox = File(root, "vault/Inbox").apply { mkdirs() }
        val note = File(inbox, "note.md").apply { writeText("original") }
        val selected = summary(note)
        note.writeText("replaced")
        assertTrue(note.setLastModified(selected.lastModifiedEpochMillis))
        val repo = FileInboxSnoozeRepository(GitSyncMutex())
        assertTrue(repo.snooze(config, root, selected, until).isFailure)
        assertEquals("replaced", note.readText())
    }

    @Test
    fun refusesSymlinksOutsideTheVault() = runBlocking {
        val root = temp.newFolder()
        val inbox = File(root, "vault/Inbox").apply { mkdirs() }
        val outside = File(root, "outside.md").apply { writeText("Outside") }
        val link = File(inbox, "note.md")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        val repo = FileInboxSnoozeRepository(GitSyncMutex())
        assertTrue(repo.snooze(config, root, summary(link), until).isFailure)
        assertEquals("Outside", outside.readText())
    }

    @Test
    fun unsafeFrontmatterLeavesFileUnchanged() = runBlocking {
        val root = temp.newFolder()
        val inbox = File(root, "vault/Inbox").apply { mkdirs() }
        val original = "---\ntags: [broken\n---\nBody"
        val note = File(inbox, "note.md").apply { writeText(original) }
        val repo = FileInboxSnoozeRepository(GitSyncMutex())
        assertTrue(repo.snooze(config, root, summary(note), until).isFailure)
        assertEquals(original, note.readText())
    }

    private fun summary(file: File) = NoteSummary(
        id = NoteId("Inbox/note.md"),
        title = "Note",
        snippet = "",
        isInbox = true,
        lastModifiedEpochMillis = file.lastModified(),
        sizeBytes = file.length(),
        inboxContentSha256 = InboxContentHash.of(file)
    )
}

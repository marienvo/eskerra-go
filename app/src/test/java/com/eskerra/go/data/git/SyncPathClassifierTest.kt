package com.eskerra.go.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPathClassifierTest {

    @Test
    fun partition_inboxPaths() {
        val partition = SyncPathClassifier.partition(
            setOf("Inbox/note.md", "Inbox/nested/other.md")
        )
        assertEquals(setOf("Inbox/note.md"), partition.inboxPaths)
        assertEquals(setOf("Inbox/nested/other.md"), partition.nonInboxPaths)
        assertTrue(partition.unsafePaths.isEmpty())
    }

    @Test
    fun partition_rejectsDeepHubInboxPaths() {
        val partition = SyncPathClassifier.partition(
            setOf("Daily/Inbox/note.md", "Daily/Inbox/nested/other.md")
        )
        assertEquals(setOf("Daily/Inbox/note.md"), partition.inboxPaths)
        assertEquals(setOf("Daily/Inbox/nested/other.md"), partition.nonInboxPaths)
    }

    @Test
    fun partition_hubInboxPaths() {
        val partition = SyncPathClassifier.partition(
            setOf("Daily/Inbox/note.md", "Weekly/Inbox/other.md", "Daily/Inbox")
        )
        assertEquals(
            setOf("Daily/Inbox/note.md", "Weekly/Inbox/other.md", "Daily/Inbox"),
            partition.inboxPaths
        )
        assertTrue(partition.nonInboxPaths.isEmpty())
        assertTrue(partition.unsafePaths.isEmpty())
    }

    @Test
    fun partition_nonInboxPaths() {
        val partition = SyncPathClassifier.partition(setOf("Projects/read.md"))
        assertTrue(partition.inboxPaths.isEmpty())
        assertEquals(setOf("Projects/read.md"), partition.nonInboxPaths)
        assertTrue(partition.unsafePaths.isEmpty())
    }

    @Test
    fun partition_unsafePaths() {
        val partition = SyncPathClassifier.partition(
            setOf(".git/config", "notes/../escape.md")
        )
        assertTrue(partition.inboxPaths.isEmpty())
        assertTrue(partition.nonInboxPaths.isEmpty())
        assertEquals(2, partition.unsafePaths.size)
    }

    @Test
    fun partition_mixedPaths() {
        val partition = SyncPathClassifier.partition(
            setOf("Inbox/a.md", "Archive/b.md", ".git/HEAD")
        )
        assertEquals(setOf("Inbox/a.md"), partition.inboxPaths)
        assertEquals(setOf("Archive/b.md"), partition.nonInboxPaths)
        assertEquals(setOf(".git/HEAD"), partition.unsafePaths)
    }

    @Test
    fun isPodcastPath_acceptsStubFile() {
        assertTrue(SyncPathClassifier.isPodcastPath("General/2026 News - podcasts.md"))
    }

    @Test
    fun isPodcastPath_acceptsRssCacheFile() {
        val name = "General/" + String(Character.toChars(0x1F4FB)) + " Daily News.md"
        assertTrue(SyncPathClassifier.isPodcastPath(name))
    }

    @Test
    fun isPodcastPath_rejectsRegularGeneralNote() {
        assertFalse(SyncPathClassifier.isPodcastPath("General/Some note.md"))
    }

    @Test
    fun isPodcastPath_rejectsNonGeneralAndUnsafePaths() {
        assertFalse(SyncPathClassifier.isPodcastPath("Inbox/2026 News - podcasts.md"))
        assertFalse(SyncPathClassifier.isPodcastPath("General/../escape - podcasts.md"))
        assertFalse(SyncPathClassifier.isPodcastPath(".git/config"))
    }
}

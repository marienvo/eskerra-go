package com.eskerra.go.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPathClassifierTest {

    @Test
    fun partition_inboxPaths() {
        val partition = SyncPathClassifier.partition(
            setOf("Inbox/note.md", "Inbox/nested/other.md")
        )
        assertEquals(setOf("Inbox/note.md", "Inbox/nested/other.md"), partition.inboxPaths)
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
}

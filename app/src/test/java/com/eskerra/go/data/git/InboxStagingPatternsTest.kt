package com.eskerra.go.data.git

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InboxStagingPatternsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun discover_alwaysIncludesRootInbox() {
        val workspace = temp.newFolder("workspace")

        val patterns = InboxStagingPatterns.discover(workspace)

        assertEquals(listOf("Inbox/"), patterns)
    }

    @Test
    fun discover_includesHubInboxWhenTodayHubExists() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Daily/Today.md", "# Today")
        write(workspace, "Work/Today.md", "# Work")

        val patterns = InboxStagingPatterns.discover(workspace)

        assertEquals(listOf("Daily/Inbox/", "Inbox/", "Work/Inbox/"), patterns)
    }

    @Test
    fun discover_ignoresInboxUnderNonHubDirectories() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Projects/Inbox/note.md", "# Note")

        val patterns = InboxStagingPatterns.discover(workspace)

        assertEquals(listOf("Inbox/"), patterns)
        assertFalse(patterns.contains("Projects/Inbox/"))
    }

    @Test
    fun discover_skipsExcludedDirectories() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Assets/Today.md", "# Hidden")

        val patterns = InboxStagingPatterns.discover(workspace)

        assertEquals(listOf("Inbox/"), patterns)
    }

    private fun write(workspace: File, relativePath: String, content: String) {
        val file = File(workspace, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}

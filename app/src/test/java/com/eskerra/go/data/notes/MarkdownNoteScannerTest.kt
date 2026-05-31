package com.eskerra.go.data.notes

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarkdownNoteScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scanner = MarkdownNoteScanner()

    @Test
    fun scan_discoversMarkdownFiles() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/hello.md", "# Hello\n\nBody line.")
        write(workspace, "Notes/other.md", "# Other\n\nMore text.")

        val registry = scanner.scan(workspace).getOrThrow()

        assertEquals(2, registry.notes.size)
    }

    @Test
    fun scan_ignoresNonMarkdownFiles() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/note.md", "# Note")
        write(workspace, "Inbox/ignore.txt", "plain text")
        write(workspace, "Inbox/image.png", "fake png")
        write(workspace, "Inbox/no-extension", "no extension")

        val registry = scanner.scan(workspace).getOrThrow()

        assertEquals(1, registry.notes.size)
        assertEquals("Inbox/note.md", registry.notes.single().id.value)
    }

    @Test
    fun scan_ignoresFilesInsideDotGit() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/visible.md", "# Visible")
        write(workspace, ".git/hooks/sample.md", "# Hidden")

        val registry = scanner.scan(workspace).getOrThrow()

        assertEquals(1, registry.notes.size)
        assertEquals("Inbox/visible.md", registry.notes.single().id.value)
    }

    @Test
    fun scan_marksInboxDirectoryNotesAsInbox() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/example.md", "# Example")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertTrue(note.isInbox)
    }

    @Test
    fun scan_marksNestedInboxDirectoryNotesAsInbox() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/sub/example.md", "# Example")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertTrue(note.isInbox)
    }

    @Test
    fun scan_doesNotMarkNotesDirectoryAsInbox() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Notes/example.md", "# Example")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertFalse(note.isInbox)
    }

    @Test
    fun scan_doesNotMarkSimilarInboxNamesAsInbox() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "SomeInbox/example.md", "# One")
        write(workspace, "inbox/example.md", "# Two")
        write(workspace, "MyInbox/example.md", "# Three")
        write(workspace, "inboxish/example.md", "# Four")
        write(workspace, "NotInbox/example.md", "# Five")

        val registry = scanner.scan(workspace).getOrThrow()

        assertTrue(registry.notes.all { !it.isInbox })
    }

    @Test
    fun scan_extractsTitleFromH1() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/titled.md", "# My Title\n\nSnippet line.")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertEquals("My Title", note.title)
        assertEquals("Snippet line.", note.snippet)
    }

    @Test
    fun scan_fallsBackToFilenameWhenNoH1() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/no-title.md", "Just body text.")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertEquals("no-title", note.title)
    }

    @Test
    fun scan_producesEmptySnippetWhenNoBodyLine() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/title-only.md", "# Title Only")

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertEquals("", note.snippet)
    }

    @Test
    fun scan_returnsEmptyRegistryForEmptyWorkspace() {
        val workspace = temp.newFolder("workspace")

        val registry = scanner.scan(workspace).getOrThrow()

        assertTrue(registry.notes.isEmpty())
        assertTrue(registry.inboxSummaries.isEmpty())
    }

    @Test
    fun scan_sortsResultsByRelativePath() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/z-last.md", "# Z")
        write(workspace, "Inbox/a-first.md", "# A")
        write(workspace, "Inbox/m-middle.md", "# M")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(
            listOf("Inbox/a-first.md", "Inbox/m-middle.md", "Inbox/z-last.md"),
            ids
        )
    }

    @Test
    fun scan_inboxSummariesExcludeNonInboxNotes() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/inbox.md", "# Inbox")
        write(workspace, "Notes/private.md", "# Private")

        val registry = scanner.scan(workspace).getOrThrow()

        assertEquals(1, registry.inboxSummaries.size)
        assertEquals("Inbox/inbox.md", registry.inboxSummaries.single().id.value)
    }

    @Test
    fun scan_acceptsMarkdownExtensionCaseInsensitively() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/alt.markdown", "# Alt")
        write(workspace, "Inbox/upper.MD", "# Upper")
        write(workspace, "Inbox/upper-alt.MARKDOWN", "# Upper Alt")

        val registry = scanner.scan(workspace).getOrThrow()

        assertEquals(3, registry.notes.size)
    }

    @Test
    fun scan_doesNotFollowSymlinkDirectories() {
        val workspace = temp.newFolder("workspace")
        val external = temp.newFolder("external")
        write(external, "hidden.md", "# Hidden")
        File(workspace, "Inbox").mkdirs()

        try {
            Files.createSymbolicLink(
                File(workspace, "Inbox/link").toPath(),
                external.toPath()
            )
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: SecurityException) {
            return
        }

        val registry = scanner.scan(workspace).getOrThrow()

        assertTrue(registry.notes.isEmpty())
    }

    @Test
    fun scan_doesNotFollowSymlinkFiles() {
        val workspace = temp.newFolder("workspace")
        val external = temp.newFolder("external")
        write(external, "secret.md", "# Secret\n\nOutside workspace.")
        File(workspace, "Inbox").mkdirs()

        try {
            Files.createSymbolicLink(
                File(workspace, "Inbox/link.md").toPath(),
                File(external, "secret.md").toPath()
            )
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: SecurityException) {
            return
        }

        val registry = scanner.scan(workspace).getOrThrow()

        assertTrue(registry.notes.isEmpty())
    }

    @Test
    fun scan_inboxSummariesSortByLastModifiedDescending() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/older.md", "# Older")
        write(workspace, "Inbox/newer.md", "# Newer")
        write(workspace, "Inbox/middle.md", "# Middle")
        File(workspace, "Inbox/older.md").setLastModified(1_000L)
        File(workspace, "Inbox/middle.md").setLastModified(2_000L)
        File(workspace, "Inbox/newer.md").setLastModified(3_000L)

        val inboxIds = scanner.scan(workspace).getOrThrow().inboxSummaries.map { it.id.value }

        assertEquals(
            listOf("Inbox/newer.md", "Inbox/middle.md", "Inbox/older.md"),
            inboxIds
        )
    }

    @Test
    fun scan_capturesLastModifiedEpochMillis() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/timestamped.md", "# Timestamped")
        File(workspace, "Inbox/timestamped.md").setLastModified(4_200L)

        val note = scanner.scan(workspace).getOrThrow().notes.single()

        assertEquals(4_200L, note.lastModifiedEpochMillis)
    }

    private fun write(workspace: File, relativePath: String, content: String) {
        val file = File(workspace, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}

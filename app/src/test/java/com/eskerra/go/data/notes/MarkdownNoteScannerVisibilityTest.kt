package com.eskerra.go.data.notes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies that [MarkdownNoteScanner] applies the vault visibility filter from
 * [com.eskerra.go.core.vault.VaultVisibility]: excluded dir names, dot-prefixed
 * names, and sync-conflict filenames. See spec §2.2.
 */
class MarkdownNoteScannerVisibilityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scanner = MarkdownNoteScanner()

    // --- Excluded directory names ---

    @Test
    fun scan_excludesAssetsDirectory() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Assets/attachment.md", "# Attachment")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    @Test
    fun scan_excludesExcalidrawDirectory() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Excalidraw/diagram.md", "# Diagram")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    @Test
    fun scan_excludesScriptsDirectory() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Scripts/gen.md", "# Gen")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    @Test
    fun scan_excludesTemplatesDirectory() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Templates/daily.md", "# Daily")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    @Test
    fun scan_excludesNestedAssetsDirectory() {
        val workspace = temp.newFolder("workspace")
        // Spec: "the filter runs at every recursion level, so e.g. Projects/Assets/private.md is excluded"
        write(workspace, "Projects/Assets/private.md", "# Private")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    // --- Dot-prefixed directory names ---

    @Test
    fun scan_excludesDotPrefixedDirectory() {
        val workspace = temp.newFolder("workspace")
        write(workspace, ".eskerra/settings.md", "# Settings")
        write(workspace, ".hidden/note.md", "# Hidden")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    // --- Dot-prefixed files ---

    @Test
    fun scan_excludesDotPrefixedMarkdownFile() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/.hidden.md", "# Hidden")
        write(workspace, "Inbox/visible.md", "# Visible")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/visible.md"), ids)
    }

    // --- Sync-conflict filenames ---

    @Test
    fun scan_excludesSyncConflictFiles() {
        val workspace = temp.newFolder("workspace")
        write(
            workspace,
            "Inbox/note.md.sync-conflict-20240101-120000-ABCDEF.md",
            "# Conflict copy"
        )
        write(workspace, "Inbox/note.md", "# Original")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/note.md"), ids)
    }

    @Test
    fun scan_allowsNormalFilesAlongExcludedSiblings() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Inbox/note.md", "# Keep")
        write(workspace, "Inbox/.dot.md", "# Drop dot")
        write(workspace, "Inbox/note.md.sync-conflict-XYZ.md", "# Drop conflict")

        val ids = scanner.scan(workspace).getOrThrow().notes.map { it.id.value }

        assertEquals(listOf("Inbox/note.md"), ids)
    }

    @Test
    fun scan_emptyWhenAllFilesExcluded() {
        val workspace = temp.newFolder("workspace")
        write(workspace, "Assets/img.md", "# Img")
        write(workspace, "Excalidraw/diag.md", "# Diag")
        write(workspace, ".eskerra/config.md", "# Config")

        assertTrue(scanner.scan(workspace).getOrThrow().notes.isEmpty())
    }

    private fun write(workspace: File, relativePath: String, content: String) {
        val file = File(workspace, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}

package com.eskerra.go.core.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultVisibilityTest {

    // --- isExcludedDirectorySegment ---

    @Test
    fun isExcludedDirectorySegment_excludesAssets() {
        assertTrue(VaultVisibility.isExcludedDirectorySegment("Assets"))
    }

    @Test
    fun isExcludedDirectorySegment_excludesExcalidraw() {
        assertTrue(VaultVisibility.isExcludedDirectorySegment("Excalidraw"))
    }

    @Test
    fun isExcludedDirectorySegment_excludesScripts() {
        assertTrue(VaultVisibility.isExcludedDirectorySegment("Scripts"))
    }

    @Test
    fun isExcludedDirectorySegment_excludesTemplates() {
        assertTrue(VaultVisibility.isExcludedDirectorySegment("Templates"))
    }

    @Test
    fun isExcludedDirectorySegment_excludesDotPrefixed() {
        assertTrue(VaultVisibility.isExcludedDirectorySegment(".eskerra"))
        assertTrue(VaultVisibility.isExcludedDirectorySegment(".git"))
        assertTrue(VaultVisibility.isExcludedDirectorySegment(".hidden"))
    }

    @Test
    fun isExcludedDirectorySegment_allowsNormalDirectories() {
        assertFalse(VaultVisibility.isExcludedDirectorySegment("Inbox"))
        assertFalse(VaultVisibility.isExcludedDirectorySegment("General"))
        assertFalse(VaultVisibility.isExcludedDirectorySegment("Projects"))
        assertFalse(VaultVisibility.isExcludedDirectorySegment("Today"))
    }

    @Test
    fun isExcludedDirectorySegment_isCaseSensitive() {
        assertFalse(VaultVisibility.isExcludedDirectorySegment("assets"))
        assertFalse(VaultVisibility.isExcludedDirectorySegment("ASSETS"))
        assertFalse(VaultVisibility.isExcludedDirectorySegment("excalidraw"))
    }

    // --- isEligibleMarkdownFileName ---

    @Test
    fun isEligibleMarkdownFileName_allowsNormalMarkdown() {
        assertTrue(VaultVisibility.isEligibleMarkdownFileName("note.md"))
        assertTrue(VaultVisibility.isEligibleMarkdownFileName("Today.md"))
        assertTrue(VaultVisibility.isEligibleMarkdownFileName("2025-01-15.md"))
        assertTrue(VaultVisibility.isEligibleMarkdownFileName("My Note.md"))
    }

    @Test
    fun isEligibleMarkdownFileName_rejectsDotPrefixed() {
        assertFalse(VaultVisibility.isEligibleMarkdownFileName(".hidden.md"))
        assertFalse(VaultVisibility.isEligibleMarkdownFileName(".obsidian.md"))
    }

    @Test
    fun isEligibleMarkdownFileName_rejectsSyncConflicts() {
        assertFalse(
            VaultVisibility.isEligibleMarkdownFileName(
                "note.md.sync-conflict-20240101-120000-ABC.md"
            )
        )
        assertFalse(
            VaultVisibility.isEligibleMarkdownFileName("2025-06-01.md.sync-conflict-XYZ.md")
        )
    }

    @Test
    fun isEligibleMarkdownFileName_allowsFilesContainingConflictElsewhere() {
        // Only rejects when the pattern is `.md.sync-conflict-`
        assertTrue(VaultVisibility.isEligibleMarkdownFileName("sync-conflict-note.md"))
    }

    // --- isSyncConflictFileName ---

    @Test
    fun isSyncConflictFileName_detectsPattern() {
        assertTrue(
            VaultVisibility.isSyncConflictFileName("note.md.sync-conflict-20240101-ABCDEF.md")
        )
    }

    @Test
    fun isSyncConflictFileName_rejectsNormalName() {
        assertFalse(VaultVisibility.isSyncConflictFileName("note.md"))
    }
}

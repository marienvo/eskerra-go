package com.eskerra.go.core.vault

/** Mirrors `packages/eskerra-core/src/vaultVisibility.ts`. */
object VaultVisibility {

    private val EXCLUDED_DIR_NAMES = setOf("Assets", "Excalidraw", "Scripts", "Templates")

    /**
     * Returns true if a directory with this name should be skipped entirely —
     * including all subdirectories. Applied at every depth, so
     * `Projects/Assets/private.md` is excluded because `Assets` appears in the path.
     */
    fun isExcludedDirectorySegment(name: String): Boolean =
        name in EXCLUDED_DIR_NAMES || name.startsWith(".")

    /**
     * Returns true if a markdown file with this name is eligible for indexing.
     * Rejects dot-prefixed names and sync-conflict filenames.
     */
    fun isEligibleMarkdownFileName(name: String): Boolean {
        return !name.startsWith(".") && !isSyncConflictFileName(name)
    }

    /** Returns true for `*.md.sync-conflict-*` filenames (Syncthing conflict pattern). */
    fun isSyncConflictFileName(name: String): Boolean =
        name.contains(".md.sync-conflict-")
}

package com.eskerra.go.data.git

import java.io.File
import org.eclipse.jgit.ignore.FastIgnoreRule
import org.eclipse.jgit.ignore.IgnoreNode

/**
 * Evaluates whether a vault-root-relative path is ignored by the vault's git ignore
 * rules, used as the download gate for the R2 binaries sync (only download files git
 * would ignore, so tracked files are never clobbered).
 *
 * Rules are loaded from the root `.gitignore` plus `.git/info/exclude`. Precedence:
 * `.gitignore` wins over `info/exclude`, so exclude rules are parsed first and
 * gitignore rules second (JGit's [IgnoreNode] applies last-match-wins).
 *
 * Known limitation: nested per-directory `.gitignore` files are not consulted — only
 * the root `.gitignore` and `info/exclude`. This covers the common case where the
 * binaries target directory is listed at the vault root.
 */
class GitignoreMatcher private constructor(private val node: IgnoreNode) {

    /**
     * True when git would ignore [relPath]. A path is ignored when any ancestor
     * directory is ignored, or when the file itself matches an ignore rule.
     */
    fun isIgnored(relPath: String): Boolean {
        val segments = relPath.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false

        // An ignored ancestor directory ignores everything beneath it (git does not
        // allow re-including a file whose parent directory is excluded).
        var prefix = ""
        for (index in 0 until segments.size - 1) {
            prefix = if (prefix.isEmpty()) segments[index] else "$prefix/${segments[index]}"
            if (node.checkIgnored(prefix, true) == true) return true
        }
        return node.checkIgnored(relPath, false) == true
    }

    companion object {
        fun forWorkspace(workspaceRoot: File): GitignoreMatcher {
            val rules = mutableListOf<FastIgnoreRule>()
            rules += rulesFrom(File(workspaceRoot, ".git/info/exclude"))
            rules += rulesFrom(File(workspaceRoot, ".gitignore"))
            return GitignoreMatcher(IgnoreNode(rules))
        }

        private fun rulesFrom(file: File): List<FastIgnoreRule> {
            if (!file.isFile) return emptyList()
            return file.readLines().mapNotNull { line ->
                runCatching { FastIgnoreRule(line) }.getOrNull()?.takeIf { !it.isEmpty }
            }
        }
    }
}

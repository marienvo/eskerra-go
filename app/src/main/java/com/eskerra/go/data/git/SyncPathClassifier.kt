package com.eskerra.go.data.git

import com.eskerra.go.core.model.SyncChangePartition
import com.eskerra.go.data.notes.MarkdownNoteScanner

/** Classifies repo-relative paths for manual sync write boundaries. */
object SyncPathClassifier {

    private val inboxPrefix = "${MarkdownNoteScanner.INBOX_DIRECTORY}/"

    fun partition(changedPaths: Set<String>): SyncChangePartition {
        val inbox = mutableSetOf<String>()
        val nonInbox = mutableSetOf<String>()
        val unsafe = mutableSetOf<String>()

        for (rawPath in changedPaths) {
            val path = rawPath.replace('\\', '/').trimStart('/')
            when {
                isUnsafe(path) -> unsafe += rawPath
                isInboxPath(path) -> inbox += rawPath
                else -> nonInbox += rawPath
            }
        }

        return SyncChangePartition(
            inboxPaths = inbox,
            nonInboxPaths = nonInbox,
            unsafePaths = unsafe
        )
    }

    private fun isInboxPath(path: String): Boolean = path == MarkdownNoteScanner.INBOX_DIRECTORY ||
        path.startsWith(inboxPrefix)

    private fun isUnsafe(path: String): Boolean {
        if (path.isBlank()) return true
        if (path.split('/').any { it == ".." }) return true
        if (path.split('/').any { it == ".git" }) return true
        return false
    }
}

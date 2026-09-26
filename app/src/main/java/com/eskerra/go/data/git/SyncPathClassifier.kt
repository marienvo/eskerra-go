package com.eskerra.go.data.git

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.SyncChangePartition

/** Classifies repo-relative paths for manual sync write boundaries. */
object SyncPathClassifier {

    private val inboxDirectory = InboxNotePath.INBOX_DIRECTORY

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

    private fun isInboxPath(path: String): Boolean {
        if (InboxNotePath.isInboxRelativePath(path)) return true
        val segments = path.split('/')
        return when (segments.size) {
            1 -> segments[0] == inboxDirectory
            2 -> segments[1] == inboxDirectory
            else -> false
        }
    }

    private fun isUnsafe(path: String): Boolean {
        if (path.isBlank()) return true
        if (path.split('/').any { it == ".." }) return true
        if (path.split('/').any { it == ".git" }) return true
        return false
    }
}

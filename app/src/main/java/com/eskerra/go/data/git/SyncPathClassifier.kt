package com.eskerra.go.data.git

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.model.SyncChangePartition

/** Classifies repo-relative paths for manual sync write boundaries. */
object SyncPathClassifier {

    private val inboxDirectory = InboxNotePath.INBOX_DIRECTORY
    private const val GENERAL_PREFIX = "General/"
    private const val PODCAST_STUB_SUFFIX = "- podcasts.md"
    private val rssCachePrefix = String(Character.toChars(0x1F4FB))

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

    /**
     * True when [rawPath] is an auto-managed podcast markdown file that the podcast
     * sync channel may stage and commit on its own: a `YYYY Section - podcasts.md`
     * stub or an RSS cache file (`📻 …`) under `General/`. Regular `General/` notes
     * are intentionally excluded so external edits there are never auto-committed.
     */
    fun isPodcastPath(rawPath: String): Boolean {
        val path = rawPath.replace('\\', '/').trimStart('/')
        if (isUnsafe(path)) return false
        if (!path.startsWith(GENERAL_PREFIX)) return false
        val name = path.substringAfterLast('/')
        return name.endsWith(PODCAST_STUB_SUFFIX, ignoreCase = true) ||
            name.startsWith(rssCachePrefix)
    }

    private fun isUnsafe(path: String): Boolean {
        if (path.isBlank()) return true
        if (path.split('/').any { it == ".." }) return true
        if (path.split('/').any { it == ".git" }) return true
        return false
    }
}

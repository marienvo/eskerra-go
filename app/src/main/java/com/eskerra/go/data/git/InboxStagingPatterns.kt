package com.eskerra.go.data.git

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.todayhub.TodayHubDiscovery
import com.eskerra.go.core.vault.VaultVisibility
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Discovers git add patterns for inbox directories tied to registered Today hubs. */
internal object InboxStagingPatterns {

    fun discover(workingDir: File): List<String> {
        val hubFolders = mutableSetOf("")
        val root = workingDir.canonicalFile.toPath()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (dir == root) return FileVisitResult.CONTINUE
                    val name = dir.fileName.toString()
                    if (VaultVisibility.isExcludedDirectorySegment(name)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        return FileVisitResult.CONTINUE
                    }
                    if (path.fileName.toString() != TodayHubDiscovery.TODAY_HUB_NOTE_NAME) {
                        return FileVisitResult.CONTINUE
                    }
                    val relative = root.relativize(path).toString().replace('\\', '/')
                    if (TodayHubDiscovery.isTodayHubNote(relative)) {
                        hubFolders += TodayHubDiscovery.directoryOf(relative)
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )
        return hubFolders.map(InboxNotePath::inboxPrefixFor).sorted()
    }
}

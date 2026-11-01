package com.eskerra.go.data.notes

import com.eskerra.go.core.inbox.InboxNotePath
import com.eskerra.go.core.inbox.InboxSnoozePatch
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.InboxSnoozeRepository
import com.eskerra.go.data.git.GitSyncMutex
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads and updates one direct inbox child under the shared sync mutex. */
class FileInboxSnoozeRepository(private val syncMutex: GitSyncMutex) : InboxSnoozeRepository {
    override suspend fun snooze(
        config: WorkspaceConfig,
        filesDir: File,
        note: NoteSummary,
        until: Instant
    ): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.mutex.withLock {
            runCatching {
                val path = NotePath.fromRelativePath(note.id.value).getOrThrow().value
                require(InboxNotePath.isInboxRelativePath(path)) { "Not an inbox note" }
                val root = WorkspacePaths.resolve(filesDir, config.relativePath).getOrThrow()
                require(root.isDirectory) { "Workspace missing" }
                val target = File(root, path)
                require(target.absoluteFile == target.canonicalFile) { "Invalid note path" }
                require(Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "Note missing"
                }
                require(
                    target.lastModified() == note.lastModifiedEpochMillis &&
                        target.length() == note.sizeBytes
                ) {
                    "Note changed since selection"
                }
                val originalBytes = target.readBytes()
                require(
                    note.inboxContentSha256 != null &&
                        InboxContentHash.of(originalBytes) == note.inboxContentSha256
                ) { "Note changed since selection" }
                val original = originalBytes.toString(Charsets.UTF_8)
                require(original.toByteArray(Charsets.UTF_8).contentEquals(originalBytes)) {
                    "Note is not valid UTF-8"
                }
                val updated = InboxSnoozePatch.apply(original, until).getOrThrow()
                val parent = requireNotNull(target.parentFile) { "Missing inbox directory" }
                val temporary = Files.createTempFile(parent.toPath(), ".snooze-", ".tmp")
                try {
                    Files.write(temporary, updated.toByteArray(Charsets.UTF_8))
                    require(target.readBytes().contentEquals(originalBytes)) { "Note changed" }
                    Files.move(
                        temporary,
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } finally {
                    Files.deleteIfExists(temporary)
                }
                Unit
            }
        }
    }
}

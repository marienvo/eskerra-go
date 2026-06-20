package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.vault.VaultVisibility
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Scans a workspace directory and returns indexed note summaries. */
interface NoteWorkspaceScanner {
    fun scan(workspaceDir: File, previousRegistry: NoteRegistry?): Result<NoteRegistry>
    fun scan(workspaceDir: File): Result<NoteRegistry> = scan(workspaceDir, null)
}

/**
 * Scans a workspace directory for markdown notes and builds a [NoteRegistry].
 * Only reads enough of each file to extract a title and snippet.
 */
class MarkdownNoteScanner : NoteWorkspaceScanner {

    override fun scan(workspaceDir: File, previousRegistry: NoteRegistry?): Result<NoteRegistry> {
        val root = workspaceDir.canonicalFile
        val summaries = mutableListOf<NoteSummary>()
        val previousByPath = previousRegistry?.notes?.associateBy { it.id.value } ?: emptyMap()

        try {
            Files.walkFileTree(
                root.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        val name = dir.fileName.toString()
                        if (VaultVisibility.isExcludedDirectorySegment(name)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        path: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            return FileVisitResult.CONTINUE
                        }

                        val file = path.toFile()
                        if (!isMarkdownFile(file)) return FileVisitResult.CONTINUE
                        if (!VaultVisibility.isEligibleMarkdownFileName(file.name)) {
                            return FileVisitResult.CONTINUE
                        }

                        val relativePath = root.toPath()
                            .relativize(path)
                            .toString()
                            .replace('\\', '/')
                        val notePath = NotePath.fromRelativePath(relativePath).getOrElse { error ->
                            throw NoteIndexException(NoteIndexError.ScanFailed(error.message))
                        }
                        val isInbox = isInboxNote(notePath.value)
                        val mtime = attrs.lastModifiedTime().toMillis()
                        val size = attrs.size()
                        val previous = previousByPath[relativePath]
                        val (title, snippet) = if (
                            previous != null &&
                            previous.lastModifiedEpochMillis == mtime &&
                            previous.sizeBytes == size
                        ) {
                            previous.title to previous.snippet
                        } else {
                            extractTitleAndSnippet(file)
                        }

                        summaries += NoteSummary(
                            id = NoteId(notePath.value),
                            title = title,
                            snippet = snippet,
                            isInbox = isInbox,
                            lastModifiedEpochMillis = mtime,
                            sizeBytes = size
                        )
                        return FileVisitResult.CONTINUE
                    }
                }
            )
        } catch (error: NoteIndexException) {
            return Result.failure(error)
        } catch (error: Exception) {
            return Result.failure(
                NoteIndexException(NoteIndexError.ScanFailed(error.message))
            )
        }

        return Result.success(NoteRegistry.fromNotes(summaries))
    }

    private fun isMarkdownFile(file: File): Boolean {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex < 0) return false
        val extension = name.substring(dotIndex + 1)
        return extension.equals(MARKDOWN_EXTENSION, ignoreCase = true) ||
            extension.equals(MARKDOWN_ALT_EXTENSION, ignoreCase = true)
    }

    private fun isInboxNote(relativePath: String): Boolean {
        val firstSegment = relativePath.substringBefore('/')
        return firstSegment == INBOX_DIRECTORY
    }

    private fun extractTitleAndSnippet(file: File): Pair<String, String> {
        val fallbackTitle = file.nameWithoutExtension
        var title: String? = null
        var titleLineIndex = -1
        var snippet: String? = null

        file.bufferedReader().use { reader ->
            var index = 0
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (title == null &&
                    trimmed.startsWith(H1_PREFIX) &&
                    trimmed.length > H1_PREFIX.length
                ) {
                    title = trimmed.removePrefix(H1_PREFIX).trim()
                    titleLineIndex = index
                }
                if (snippet == null && index != titleLineIndex && trimmed.isNotBlank()) {
                    snippet = trimmed
                }
                if (title != null && snippet != null) break
                index++
            }
        }

        return (title ?: fallbackTitle) to (snippet ?: "")
    }

    companion object {
        const val INBOX_DIRECTORY = "Inbox"
        private const val MARKDOWN_EXTENSION = "md"
        private const val MARKDOWN_ALT_EXTENSION = "markdown"
        private const val H1_PREFIX = "# "
    }
}

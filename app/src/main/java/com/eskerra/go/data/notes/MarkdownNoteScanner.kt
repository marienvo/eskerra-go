package com.eskerra.go.data.notes

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteIndexError
import com.eskerra.go.core.model.NoteIndexException
import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Scans a workspace directory and returns indexed note summaries. */
fun interface NoteWorkspaceScanner {
    fun scan(workspaceDir: File): Result<NoteRegistry>
}

/**
 * Scans a workspace directory for markdown notes and builds a [NoteRegistry].
 * Only reads enough of each file to extract a title and snippet.
 */
class MarkdownNoteScanner : NoteWorkspaceScanner {

    override fun scan(workspaceDir: File): Result<NoteRegistry> {
        val root = workspaceDir.canonicalFile
        val summaries = mutableListOf<NoteSummary>()

        try {
            Files.walkFileTree(
                root.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (dir.fileName.toString() == GIT_DIRECTORY) {
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

                        val relativePath = root.toPath()
                            .relativize(path)
                            .toString()
                            .replace('\\', '/')
                        val notePath = NotePath.fromRelativePath(relativePath).getOrElse { error ->
                            throw NoteIndexException(NoteIndexError.ScanFailed(error.message))
                        }
                        val isInbox = isInboxNote(notePath.value)
                        val (title, snippet) = extractTitleAndSnippet(file)

                        summaries += NoteSummary(
                            id = NoteId(notePath.value),
                            title = title,
                            snippet = snippet,
                            isInbox = isInbox
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
        val lines = file.readText().lines()
        var title: String? = null
        var titleLineIndex = -1

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(H1_PREFIX) && trimmed.length > H1_PREFIX.length) {
                title = trimmed.removePrefix(H1_PREFIX).trim()
                titleLineIndex = index
                break
            }
        }

        val resolvedTitle = title ?: fallbackTitle
        val snippet = lines.asSequence()
            .withIndex()
            .filter { (index, line) ->
                index != titleLineIndex && line.isNotBlank()
            }
            .map { (_, line) -> line.trim() }
            .firstOrNull()
            .orEmpty()

        return resolvedTitle to snippet
    }

    companion object {
        const val INBOX_DIRECTORY = "Inbox"
        private const val GIT_DIRECTORY = ".git"
        private const val MARKDOWN_EXTENSION = "md"
        private const val MARKDOWN_ALT_EXTENSION = "markdown"
        private const val H1_PREFIX = "# "
    }
}

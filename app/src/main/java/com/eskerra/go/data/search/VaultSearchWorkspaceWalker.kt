package com.eskerra.go.data.search

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.vault.VaultVisibility
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal data class VaultSearchFileEntry(
    val uri: String,
    val relPath: String,
    val filename: String,
    val title: String,
    val body: String,
    val size: Long,
    val lastModified: Long
)

internal object VaultSearchWorkspaceWalker {
    private const val MARKDOWN_EXTENSION = "md"
    private const val MARKDOWN_ALT_EXTENSION = "markdown"
    private const val H1_PREFIX = "# "
    private const val MAX_FILE_BYTES = 512 * 1024

    fun walk(workspaceDir: File): List<VaultSearchFileEntry> {
        val root = workspaceDir.canonicalFile
        val entries = mutableListOf<VaultSearchFileEntry>()
        Files.walkFileTree(
            root.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (VaultVisibility.isExcludedDirectorySegment(dir.fileName.toString())) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
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
                    if (NotePath.fromRelativePath(relativePath).isFailure) {
                        return FileVisitResult.CONTINUE
                    }
                    val (title, body) = readTitleAndBody(file)
                    entries += VaultSearchFileEntry(
                        uri = relativePath,
                        relPath = relativePath,
                        filename = file.name,
                        title = title,
                        body = body,
                        size = attrs.size(),
                        lastModified = attrs.lastModifiedTime().toMillis()
                    )
                    return FileVisitResult.CONTINUE
                }
            }
        )
        return entries
    }

    fun snapshot(workspaceDir: File): Map<String, com.eskerra.go.core.search.FileSnapshot> =
        walk(workspaceDir).associate { entry ->
            entry.uri to com.eskerra.go.core.search.FileSnapshot(entry.size, entry.lastModified)
        }

    private fun isMarkdownFile(file: File): Boolean {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex < 0) return false
        val extension = name.substring(dotIndex + 1)
        return extension.equals(MARKDOWN_EXTENSION, ignoreCase = true) ||
            extension.equals(MARKDOWN_ALT_EXTENSION, ignoreCase = true)
    }

    private fun readTitleAndBody(file: File): Pair<String, String> {
        if (file.length() > MAX_FILE_BYTES) {
            return file.nameWithoutExtension to ""
        }
        val text = runCatching { file.readText() }.getOrDefault("")
        val lines = text.lines()
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
        val resolvedTitle = title ?: file.nameWithoutExtension
        val body = lines.asSequence()
            .withIndex()
            .filter { (index, line) -> index != titleLineIndex && line.isNotBlank() }
            .joinToString("\n") { (_, line) -> line }
        return resolvedTitle to body
    }
}

package com.eskerra.go.core.attachments

import com.eskerra.go.core.markdown.VaultLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.vault.VaultLayout
import java.net.URLDecoder

/**
 * Vault attachment path helpers for read-only image rendering (spec §13).
 *
 * Mirrors `packages/eskerra-core/src/attachments/attachmentPaths.ts` and the desktop shell's
 * `resolveVaultImagePreviewUrl` behaviour, adapted for eskerra-go vault-relative [NoteId] paths.
 */
object AttachmentPaths {

    val ATTACHMENT_IMAGE_EXTENSIONS: List<String> = listOf(
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".webp",
        ".svg"
    )

    private val extensionSet = ATTACHMENT_IMAGE_EXTENSIONS.toSet()
    private val edgeTrimChars = setOf('-', '_')
    private const val INBOX_DIR = "Inbox"

    sealed interface ResolvedImageSrc {
        data class Remote(val url: String) : ResolvedImageSrc

        /** Vault-relative path such as `Assets/Attachments/a.png`. */
        data class Local(val vaultRelativePath: String) : ResolvedImageSrc

        data class Unresolvable(val originalSrc: String) : ResolvedImageSrc
    }

    fun normalizeImageFileExtension(ext: String): String? {
        val trimmed = ext.trim().lowercase()
        if (trimmed.isEmpty()) return null
        val withDot = if (trimmed.startsWith('.')) trimmed else ".$trimmed"
        return withDot.takeIf { it in extensionSet }
    }

    fun imageMimeToExtension(mime: String): String? = when (mime.trim().lowercase()) {
        "image/png" -> ".png"
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        else -> null
    }

    fun sanitizeAttachmentBaseName(rawName: String): String {
        val withoutSeparators = buildString {
            for (character in rawName) {
                if (character != '/' && character != '\\') {
                    append(character)
                }
            }
        }.trim()
        val dot = withoutSeparators.lastIndexOf('.')
        val withoutExt = if (dot >= 0) withoutSeparators.substring(0, dot) else withoutSeparators
        val kept = buildString {
            for (character in withoutExt.lowercase()) {
                if (character.isDigit() ||
                    character in 'a'..'z' ||
                    character == '-' ||
                    character == '_' ||
                    character == ' '
                ) {
                    append(character)
                }
            }
        }
        val spaced = collapseAsciiWhitespaceRunsToSpace(kept).trim()
        val hyphenated = if (spaced.contains(' ')) spaced.split(' ').joinToString("-") else spaced
        val collapsed = collapseRunsOfChar(hyphenated, '-')
        val normalized = trimEdgeChars(trimEdgeChars(collapsed, edgeTrimChars), edgeTrimChars)
        return normalized.ifEmpty { "image" }
    }

    fun buildAttachmentFileName(
        stem: String,
        extensionWithDot: String,
        uniqueToken: String
    ): String {
        val ext = extensionWithDot.trim().lowercase().let { raw ->
            if (raw.startsWith('.')) raw else ".$raw"
        }
        require(ext in extensionSet) { "Unsupported attachment extension: $ext" }
        val safeStem = stem.trim().ifEmpty { "image" }
        val token = uniqueToken.trim().ifEmpty { "0" }
        return "$safeStem-$token$ext"
    }

    fun inboxNoteRelativeAttachmentDir(): String =
        "../${VaultLayout.ASSETS_DIRECTORY_NAME}/${VaultLayout.ATTACHMENTS_DIRECTORY_NAME}"

    fun buildInboxRelativeAttachmentMarkdownPath(attachmentFileName: String): String {
        require(!attachmentFileName.contains('/') && !attachmentFileName.contains('\\')) {
            "Attachment file name must not contain path separators"
        }
        require(attachmentFileName != "." && attachmentFileName != "..") {
            "Invalid attachment file name"
        }
        return "${inboxNoteRelativeAttachmentDir()}/$attachmentFileName"
    }

    /**
     * Resolves markdown image `src` for display. Remote URLs pass through unchanged; relative paths
     * resolve against the source note directory (or [INBOX_DIR] when [sourceNoteId] is null).
     */
    fun resolveVaultImageLoadTarget(sourceNoteId: NoteId?, src: String): ResolvedImageSrc {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return ResolvedImageSrc.Unresolvable(trimmed)
        if (isRemoteImageSrc(trimmed)) return ResolvedImageSrc.Remote(trimmed)

        val noteDir = sourceNoteId?.value?.substringBeforeLast('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
            ?: INBOX_DIR
        val pathForResolve = decodeAttachmentSrcForFilesystem(trimmed)
        val vaultRelative = VaultLink.posixResolveRelativeToDirectory(noteDir, pathForResolve)
        if (vaultRelative.isEmpty() || !hasAllowedImageExtension(vaultRelative)) {
            return ResolvedImageSrc.Unresolvable(trimmed)
        }
        return ResolvedImageSrc.Local(vaultRelative)
    }

    fun hasAllowedImageExtension(path: String): Boolean {
        val lower = path.lowercase()
        return ATTACHMENT_IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isSvgPath(path: String): Boolean = path.lowercase().endsWith(".svg")

    private fun isRemoteImageSrc(src: String): Boolean {
        val lower = src.lowercase()
        return lower.startsWith("https://") ||
            lower.startsWith("http://") ||
            lower.startsWith("data:")
    }

    private fun decodeAttachmentSrcForFilesystem(src: String): String = try {
        URLDecoder.decode(src, Charsets.UTF_8.name())
    } catch (_: Exception) {
        src
    }

    private fun collapseAsciiWhitespaceRunsToSpace(value: String): String {
        val out = StringBuilder(value.length)
        var inRun = false
        for (character in value) {
            if (character.isWhitespace()) {
                if (!inRun && out.isNotEmpty()) {
                    out.append(' ')
                    inRun = true
                }
            } else {
                out.append(character)
                inRun = false
            }
        }
        return out.toString()
    }

    private fun collapseRunsOfChar(value: String, target: Char): String {
        val out = StringBuilder(value.length)
        var previousWasTarget = false
        for (character in value) {
            if (character == target) {
                if (!previousWasTarget) {
                    out.append(character)
                    previousWasTarget = true
                }
            } else {
                out.append(character)
                previousWasTarget = false
            }
        }
        return out.toString()
    }

    private fun trimEdgeChars(value: String, chars: Set<Char>): String {
        var start = 0
        var end = value.length
        while (start < end && value[start] in chars) start++
        while (end > start && value[end - 1] in chars) end--
        return value.substring(start, end)
    }
}

package com.eskerra.go.data.git

import java.io.File
import java.text.Normalizer
import org.eclipse.jgit.api.Git

/** Stages repo-relative paths without the broad `.` add pattern. */
object GitChangeStager {

    fun stagePaths(workingDir: File, relativePaths: Iterable<String>) {
        for (rawPath in relativePaths) {
            val pattern = rawPath.replace('\\', '/').trimStart('/')
            if (pattern.isBlank()) continue
            GitIndexLockRecovery.clearStaleLock(workingDir).getOrThrow()
            Git.open(workingDir).use { git ->
                stageSinglePath(git, workingDir, pattern)
            }
        }
    }

    private fun stageSinglePath(git: Git, workingDir: File, pattern: String) {
        val existing = resolveExistingFile(workingDir, pattern)
        val pathToStage = existing
            ?.relativeTo(workingDir)
            ?.path
            ?.replace('\\', '/')
            ?: pattern
        try {
            stagePathWithFilepattern(git, pathToStage, stageContent = existing != null)
        } catch (first: Exception) {
            if (!needsLiteralPathspec(pathToStage)) {
                throw GitStageException(pathToStage, first)
            }
            try {
                stagePathWithFilepattern(
                    git = git,
                    filepattern = literalPattern(pathToStage),
                    stageContent = existing != null
                )
            } catch (second: Exception) {
                throw GitStageException(pathToStage, second)
            }
        }
    }

    private fun stagePathWithFilepattern(git: Git, filepattern: String, stageContent: Boolean) {
        if (stageContent) {
            git.add().addFilepattern(filepattern).call()
        }
        git.add().addFilepattern(filepattern).setUpdate(true).call()
    }

    internal fun literalPattern(relativePath: String): String = ":(literal)$relativePath"

    internal fun needsLiteralPathspec(relativePath: String): Boolean =
        relativePath.any { it in PATHSPEC_MAGIC_CHARACTERS || it.code > 127 }

    internal fun filepatternFor(relativePath: String): String =
        if (needsLiteralPathspec(relativePath)) {
            literalPattern(relativePath)
        } else {
            relativePath
        }

    internal fun resolveExistingFile(workingDir: File, pattern: String): File? {
        val direct = File(workingDir, pattern)
        if (direct.exists()) return direct

        val parent = direct.parentFile ?: return null
        val leaf = direct.name
        if (!parent.isDirectory) return null

        val normalizedLeaf = normalizeFileName(leaf)
        return parent.listFiles()?.firstOrNull { candidate ->
            candidate.isFile &&
                (
                    candidate.name == leaf ||
                        normalizeFileName(candidate.name) == normalizedLeaf
                    )
        }
    }

    private fun normalizeFileName(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFC)

    class GitStageException(val relativePath: String, cause: Throwable) :
        IllegalStateException("failed to stage path: $relativePath", cause)

    private val PATHSPEC_MAGIC_CHARACTERS = charArrayOf('*', '?', '[', '\\')
}

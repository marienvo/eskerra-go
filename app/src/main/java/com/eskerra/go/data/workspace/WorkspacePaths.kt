package com.eskerra.go.data.workspace

import java.io.File

/**
 * Path helpers for the fixed single-workspace directory under app-private storage.
 * Mirrors Step 2 writeFile path-safety rules at the workspace root level.
 */
object WorkspacePaths {

    const val DEFAULT_RELATIVE_PATH = "workspace"

    fun validateRelativePath(relativePath: String): Result<Unit> {
        if (relativePath.isBlank()) {
            return Result.failure(IllegalArgumentException("Workspace path must not be blank"))
        }
        if (File(relativePath).isAbsolute) {
            return Result.failure(IllegalArgumentException("Workspace path must be relative"))
        }
        if (relativePath.split('/', '\\').any { it == ".." }) {
            return Result.failure(
                IllegalArgumentException("Workspace path must not contain '..' segments")
            )
        }
        return Result.success(Unit)
    }

    fun resolve(filesDir: File, relativePath: String): Result<File> {
        validateRelativePath(relativePath).getOrElse { return Result.failure(it) }
        val resolved = File(filesDir, relativePath).canonicalFile
        if (!isInsideFilesDir(filesDir, resolved)) {
            return Result.failure(
                IllegalArgumentException("Workspace path escapes app-private storage")
            )
        }
        return Result.success(resolved)
    }

    fun isInsideFilesDir(filesDir: File, resolved: File): Boolean {
        val base = filesDir.canonicalFile
        val target = resolved.canonicalFile
        return target == base || target.path.startsWith(base.path + File.separator)
    }

    fun isValidGitWorkspace(dir: File): Boolean {
        val gitDir = File(dir, ".git")
        return gitDir.isDirectory &&
            File(gitDir, "HEAD").isFile &&
            File(gitDir, "objects").isDirectory &&
            File(gitDir, "refs").isDirectory
    }

    fun ensureEmptyDirectory(dir: File): Result<Unit> {
        if (dir.exists() && dir.listFiles()?.isNotEmpty() == true) {
            return Result.failure(IllegalStateException("Workspace directory is not empty"))
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.failure(IllegalStateException("Failed to create workspace directory"))
        }
        if (dir.listFiles()?.isNotEmpty() == true) {
            return Result.failure(IllegalStateException("Workspace directory is not empty"))
        }
        return Result.success(Unit)
    }

    fun removeWorkspaceDirectory(filesDir: File, relativePath: String) {
        resolve(filesDir, relativePath).getOrNull()
            ?.takeIf { it.exists() }
            ?.deleteRecursively()
    }
}

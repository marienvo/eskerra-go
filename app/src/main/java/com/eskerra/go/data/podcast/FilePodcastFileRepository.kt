package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastFileRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Filesystem-backed [PodcastFileRepository] for podcast markdown under the workspace. */
class FilePodcastFileRepository : PodcastFileRepository {

    override suspend fun read(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val target = resolveSafeTarget(config, filesDir, relativePath)
            if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return@runCatching null
            }
            target.readText(Charsets.UTF_8)
        }
    }

    override suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = resolveSafeTarget(config, filesDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)
        }
    }

    private fun resolveSafeTarget(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String
    ): File {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank()) { "relativePath must not be blank" }
        val segments = normalized.split('/')
        require(segments.none { it == ".." }) { "relativePath must not contain '..'" }
        require(segments.none { it == ".git" }) { "relativePath must not contain '.git'" }

        val workspaceDir = WorkspacePaths.resolve(filesDir, config.relativePath).getOrThrow()
        val base = workspaceDir.canonicalFile
        val target = File(base, normalized).canonicalFile
        require(target.path == base.path || target.path.startsWith(base.path + File.separator)) {
            "resolved path escapes workspace: $relativePath"
        }
        return target
    }
}

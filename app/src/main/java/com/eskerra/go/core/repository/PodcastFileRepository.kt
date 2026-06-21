package com.eskerra.go.core.repository

import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/**
 * Reads and writes the markdown files that back the podcast catalog (stub and RSS
 * cache files under `General/`). Kept separate from note read/write ports because
 * podcast files are not subject to inbox editability rules.
 */
interface PodcastFileRepository {

    /** Returns the UTF-8 file content, or `null` when the file does not exist. */
    suspend fun read(config: WorkspaceConfig, filesDir: File, relativePath: String): Result<String?>

    suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        relativePath: String,
        content: String
    ): Result<Unit>
}

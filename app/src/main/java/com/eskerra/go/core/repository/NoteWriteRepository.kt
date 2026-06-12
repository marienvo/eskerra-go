package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Writes UTF-8 markdown for notes in the configured workspace. */
interface NoteWriteRepository {
    suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        markdown: String
    ): Result<Unit>

    suspend fun exists(config: WorkspaceConfig, filesDir: File, notePath: NotePath): Result<Boolean>

    suspend fun delete(config: WorkspaceConfig, filesDir: File, notePath: NotePath): Result<Unit>
}

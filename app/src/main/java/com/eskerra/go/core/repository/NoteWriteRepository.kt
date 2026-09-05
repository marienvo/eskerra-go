package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NotePath
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Writes UTF-8 markdown for notes in the configured workspace. */
interface NoteWriteRepository {
    /** Immediate sibling names for [notePath], including directories, used for portable allocation. */
    suspend fun listSiblingNames(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath
    ): Result<Set<String>>

    suspend fun write(
        config: WorkspaceConfig,
        filesDir: File,
        notePath: NotePath,
        markdown: String
    ): Result<Unit>

    suspend fun exists(config: WorkspaceConfig, filesDir: File, notePath: NotePath): Result<Boolean>

    suspend fun delete(config: WorkspaceConfig, filesDir: File, notePath: NotePath): Result<Unit>
}

package com.eskerra.go.core.repository

import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.NoteflowConfig
import java.io.File

interface NoteflowRepository {
    suspend fun discover(config: WorkspaceConfig, filesDir: File): Result<List<NoteflowConfig>>
}

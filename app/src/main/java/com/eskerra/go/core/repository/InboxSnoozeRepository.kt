package com.eskerra.go.core.repository

import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File
import java.time.Instant

interface InboxSnoozeRepository {
    suspend fun snooze(
        config: WorkspaceConfig,
        filesDir: File,
        note: NoteSummary,
        until: Instant
    ): Result<Unit>
}

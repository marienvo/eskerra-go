package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PlaylistWriteResult
import com.eskerra.go.core.repository.PlaylistSyncRepository
import java.io.File

class WritePlaylist(private val repository: PlaylistSyncRepository) {
    suspend operator fun invoke(workspaceRoot: File, entry: PlaylistEntry): PlaylistWriteResult =
        repository.writePlaylist(workspaceRoot, entry)
}

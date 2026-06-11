package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.PlaylistSyncRepository
import java.io.File

class ClearPlaylist(private val repository: PlaylistSyncRepository) {
    suspend operator fun invoke(workspaceRoot: File) = repository.clearPlaylist(workspaceRoot)
}

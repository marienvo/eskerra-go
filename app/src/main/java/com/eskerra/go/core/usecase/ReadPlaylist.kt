package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.repository.PlaylistSyncRepository
import java.io.File

class ReadPlaylist(private val repository: PlaylistSyncRepository) {
    suspend operator fun invoke(workspaceRoot: File): PlaylistEntry? =
        repository.readPlaylist(workspaceRoot)
}

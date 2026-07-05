package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.DownloadedBinary
import com.eskerra.go.core.repository.BinarySyncRepository
import java.io.File

/**
 * Lists the binaries currently present on the device (manifest entries whose file
 * still exists under [workspaceRoot]), for the "Downloaded binaries" tile.
 */
class LoadDownloadedBinaries(private val repository: BinarySyncRepository) {

    suspend operator fun invoke(workspaceRoot: File): List<DownloadedBinary> =
        repository.readManifest()
            .mapNotNull { entry ->
                val size =
                    repository.localSize(workspaceRoot, entry.relPath) ?: return@mapNotNull null
                DownloadedBinary(relPath = entry.relPath, sizeBytes = size)
            }
            .sortedBy { it.relPath }
}

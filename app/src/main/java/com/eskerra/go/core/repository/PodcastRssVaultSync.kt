package com.eskerra.go.core.repository

import com.eskerra.go.core.model.WorkspaceConfig
import java.io.File

/** Determinate (`percent` set) or indeterminate progress for a vault RSS refresh. */
data class PodcastRefreshProgress(
    val percent: Int?,
    val phase: String,
    val detail: String? = null
) {
    companion object {
        const val PHASE_RSS = "rss"
        const val PHASE_MERGE = "merge"
        const val PHASE_COMPLETE = "complete"
    }
}

/** Outcome counts for one vault RSS refresh run. */
data class PodcastRssVaultSyncSummary(
    val refreshedFileCount: Int,
    val mergedStubCount: Int,
    val failedFeedCount: Int
) {
    companion object {
        val EMPTY = PodcastRssVaultSyncSummary(0, 0, 0)
    }
}

/**
 * Reads `General/`, refreshes the `📻` files referenced (unchecked) by each year's
 * companion hub, and merges fresh episodes into the `*- podcasts.md` stubs. Writes
 * markdown directly to the working tree; committing is the caller's concern.
 */
interface PodcastRssVaultSync {
    suspend fun refresh(
        config: WorkspaceConfig,
        filesDir: File,
        onProgress: (PodcastRefreshProgress) -> Unit
    ): Result<PodcastRssVaultSyncSummary>
}

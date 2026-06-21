package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSyncResult
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastFileRepository
import java.io.File

/** Result of marking one or more episodes as played. */
data class MarkPodcastEpisodesPlayedResult(
    val updatedPaths: List<String>,
    val sync: PodcastSyncResult
) {
    val updated: Boolean get() = updatedPaths.isNotEmpty()
}

/** Commits and pushes pending podcast changes; satisfied by [SyncPodcastChange]. */
typealias PodcastChangeSync = suspend (WorkspaceConfig, File) -> Result<PodcastSyncResult>

/**
 * Marks podcast episodes as played by flipping their checkbox in the backing
 * `General/` stub files, then commits the change through [syncPodcastChange] as one
 * isolated commit.
 *
 * Episodes are grouped by source file so a batch (multi-select) archive touches each
 * file once and produces a single commit. When no file actually changes (already
 * played, or episode line not found) no commit is created.
 */
class MarkPodcastEpisodesPlayed(
    private val podcastFileRepository: PodcastFileRepository,
    private val syncPodcastChange: PodcastChangeSync,
    private val markInContent: MarkPodcastEpisodePlayed = MarkPodcastEpisodePlayed(),
    private val generalDirectory: String = GENERAL_DIRECTORY
) {

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        episodes: List<PodcastEpisode>
    ): Result<MarkPodcastEpisodesPlayedResult> {
        val updatedPaths = mutableListOf<String>()

        val byFile = episodes
            .filter { it.sourceFile.isNotBlank() && it.mp3Url.isNotBlank() }
            .groupBy { it.sourceFile }

        for ((sourceFile, fileEpisodes) in byFile) {
            val relativePath = "$generalDirectory/$sourceFile"
            val original = podcastFileRepository.read(config, filesDir, relativePath)
                .getOrElse { return Result.failure(it) }
                ?: continue

            var content = original
            var changed = false
            for (episode in fileEpisodes) {
                val outcome = markInContent(content, episode.mp3Url)
                if (outcome.updated) {
                    content = outcome.content
                    changed = true
                }
            }

            if (changed) {
                podcastFileRepository.write(config, filesDir, relativePath, content)
                    .getOrElse { return Result.failure(it) }
                updatedPaths += relativePath
            }
        }

        if (updatedPaths.isEmpty()) {
            return Result.success(
                MarkPodcastEpisodesPlayedResult(
                    updatedPaths = emptyList(),
                    sync = PodcastSyncResult.NOTHING_TO_COMMIT
                )
            )
        }

        // The episodes are already flipped on disk, so a failed best-effort sync (no remote,
        // missing credential, offline, push rejected) must not surface as "could not archive".
        // The local commit rides the next successful vault sync; the archive itself succeeded.
        val sync = syncPodcastChange(config, filesDir).getOrElse { PodcastSyncResult.PENDING }
        return Result.success(
            MarkPodcastEpisodesPlayedResult(updatedPaths = updatedPaths, sync = sync)
        )
    }

    companion object {
        const val GENERAL_DIRECTORY = "General"
    }
}

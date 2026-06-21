package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.podcast.PodcastMarkdownFile
import com.eskerra.go.core.podcast.buildPodcastSections
import com.eskerra.go.core.podcast.isPodcastEpisodesFile
import com.eskerra.go.core.podcast.parsePodcastFiles
import com.eskerra.go.core.podcast.resolveSectionFeedUrls
import com.eskerra.go.core.podcast.resolveSeriesFeedUrls
import com.eskerra.go.core.podcast.rss.PodcastMarkdownLinks
import com.eskerra.go.core.repository.PodcastCatalogRepository
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Year
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilePodcastCatalogRepository(private val currentYear: () -> Int = { Year.now().value }) :
    PodcastCatalogRepository {

    override suspend fun load(config: WorkspaceConfig, filesDir: File): Result<PodcastCatalog> =
        withContext(Dispatchers.IO) {
            val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
            if (workspaceResult.isFailure) {
                return@withContext Result.failure(
                    PodcastCatalogException(PodcastCatalogError.InvalidWorkspacePath)
                )
            }

            val workspaceDir = workspaceResult.getOrThrow()
            if (!workspaceDir.isDirectory) {
                return@withContext Result.failure(
                    PodcastCatalogException(PodcastCatalogError.WorkspaceMissing)
                )
            }

            try {
                val year = currentYear()
                val generalDir = File(workspaceDir, GENERAL_DIRECTORY)
                if (!generalDir.isDirectory) {
                    return@withContext Result.success(emptyCatalog())
                }

                val stubFiles = generalDir.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file ->
                        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                            isPodcastEpisodesFile(file.name, year)
                    }
                    .sortedBy { it.name }
                    .map { file ->
                        PodcastMarkdownFile(
                            fileName = file.name,
                            content = file.readText(Charsets.UTF_8)
                        )
                    }
                    .toList()

                val parsed = parsePodcastFiles(stubFiles, year)
                val feedUrlsBySection = resolveSectionFeedUrls(generalDir, year)
                val feedUrlsBySeries = resolveSeriesFeedUrls(generalDir)
                val enrichedEpisodes = parsed.allEpisodes.map { episode ->
                    // Prefer the episode's own show feed so each podcast keeps its own artwork;
                    // fall back to the section feed when a show has no resolvable feed file.
                    val feedUrl =
                        feedUrlsBySeries[PodcastMarkdownLinks.normalizeTitleKey(episode.seriesName)]
                            ?: feedUrlsBySection[episode.sectionTitle]
                    if (feedUrl == null) episode else episode.copy(rssFeedUrl = feedUrl)
                }
                // buildPodcastSections derives each section's feed from its (already per-series
                // enriched) episodes, so episode-level artwork is preserved per show.
                val sections = buildPodcastSections(enrichedEpisodes)
                Result.success(
                    PodcastCatalog(
                        allEpisodes = enrichedEpisodes,
                        sections = sections
                    )
                )
            } catch (error: Exception) {
                Result.failure(
                    PodcastCatalogException(PodcastCatalogError.LoadFailed(error.message))
                )
            }
        }

    internal companion object {
        const val GENERAL_DIRECTORY = "General"

        fun emptyCatalog(): PodcastCatalog =
            PodcastCatalog(allEpisodes = emptyList(), sections = emptyList())
    }
}

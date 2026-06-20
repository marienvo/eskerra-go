package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastCatalogError
import com.eskerra.go.core.model.PodcastCatalogException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.podcast.PodcastMarkdownFile
import com.eskerra.go.core.podcast.buildPodcastSections
import com.eskerra.go.core.podcast.isPodcastEpisodesFile
import com.eskerra.go.core.podcast.parsePodcastFiles
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
                Result.success(
                    PodcastCatalog(
                        allEpisodes = parsed.allEpisodes,
                        sections = buildPodcastSections(parsed.allEpisodes)
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

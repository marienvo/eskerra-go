package com.eskerra.go.data.podcast

import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSection
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.PodcastCatalogSnapshotStore
import com.eskerra.go.data.workspace.GateFingerprintComputer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FilePodcastCatalogSnapshotStore : PodcastCatalogSnapshotStore {

    override suspend fun read(config: WorkspaceConfig, filesDir: File): PodcastCatalog? =
        withContext(Dispatchers.IO) {
            val file = snapshotFile(filesDir)
            if (!file.isFile) return@withContext null
            val expected = GateFingerprintComputer.compute(config, filesDir).value
            runCatching {
                val snapshot = Json.decodeFromString<StoredPodcastCatalogSnapshot>(file.readText())
                if (snapshot.workspaceFingerprint != expected) return@withContext null
                snapshot.toCatalog()
            }.getOrNull()
        }

    override suspend fun save(config: WorkspaceConfig, filesDir: File, catalog: PodcastCatalog) {
        withContext(Dispatchers.IO) {
            val file = snapshotFile(filesDir)
            file.parentFile?.mkdirs()
            val payload = StoredPodcastCatalogSnapshot(
                workspaceFingerprint = GateFingerprintComputer.compute(config, filesDir).value,
                savedAtEpochMs = System.currentTimeMillis(),
                allEpisodes = catalog.allEpisodes.map { StoredPodcastEpisode.from(it) },
                sections = catalog.sections.map { StoredPodcastSection.from(it) }
            )
            file.writeText(Json.encodeToString(payload))
        }
    }

    override suspend fun clear(config: WorkspaceConfig, filesDir: File) {
        withContext(Dispatchers.IO) {
            val file = snapshotFile(filesDir)
            if (!file.isFile) return@withContext
            val expected = GateFingerprintComputer.compute(config, filesDir).value
            runCatching {
                Json.decodeFromString<StoredPodcastCatalogSnapshot>(file.readText())
            }.getOrNull()?.takeIf { it.workspaceFingerprint == expected }?.let {
                file.delete()
            }
        }
    }

    private fun snapshotFile(filesDir: File): File =
        File(File(filesDir, CACHE_DIR), SNAPSHOT_FILE_NAME)

    companion object {
        private const val CACHE_DIR = "cache"
        private const val SNAPSHOT_FILE_NAME = "podcast_catalog_snapshot.json"
    }
}

@Serializable
private data class StoredPodcastCatalogSnapshot(
    val workspaceFingerprint: String,
    val savedAtEpochMs: Long,
    val allEpisodes: List<StoredPodcastEpisode>,
    val sections: List<StoredPodcastSection>
) {
    fun toCatalog() = PodcastCatalog(
        allEpisodes = allEpisodes.map { it.toEpisode() },
        sections = sections.map { it.toSection() }
    )
}

@Serializable
private data class StoredPodcastEpisode(
    val articleUrl: String?,
    val date: String,
    val id: String,
    val isListened: Boolean,
    val mp3Url: String,
    val rssFeedUrl: String?,
    val sectionTitle: String,
    val seriesName: String,
    val sourceFile: String,
    val title: String
) {
    fun toEpisode() = PodcastEpisode(
        articleUrl = articleUrl,
        date = date,
        id = id,
        isListened = isListened,
        mp3Url = mp3Url,
        rssFeedUrl = rssFeedUrl,
        sectionTitle = sectionTitle,
        seriesName = seriesName,
        sourceFile = sourceFile,
        title = title
    )

    companion object {
        fun from(episode: PodcastEpisode) = StoredPodcastEpisode(
            articleUrl = episode.articleUrl,
            date = episode.date,
            id = episode.id,
            isListened = episode.isListened,
            mp3Url = episode.mp3Url,
            rssFeedUrl = episode.rssFeedUrl,
            sectionTitle = episode.sectionTitle,
            seriesName = episode.seriesName,
            sourceFile = episode.sourceFile,
            title = episode.title
        )
    }
}

@Serializable
private data class StoredPodcastSection(
    val title: String,
    val rssFeedUrl: String?,
    val episodes: List<StoredPodcastEpisode>
) {
    fun toSection() = PodcastSection(
        title = title,
        rssFeedUrl = rssFeedUrl,
        episodes = episodes.map { it.toEpisode() }
    )

    companion object {
        fun from(section: PodcastSection) = StoredPodcastSection(
            title = section.title,
            rssFeedUrl = section.rssFeedUrl,
            episodes = section.episodes.map { StoredPodcastEpisode.from(it) }
        )
    }
}

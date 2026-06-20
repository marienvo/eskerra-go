package com.eskerra.go.data.podcast.artwork

import com.eskerra.go.core.model.PodcastArtworkMeta
import com.eskerra.go.core.podcast.podcastImageCacheKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PodcastArtworkMetaFile(
    val workspaceKey: String,
    val savedAtEpochMs: Long,
    val entries: Map<String, StoredPodcastArtworkMeta>
)

@Serializable
internal data class StoredPodcastArtworkMeta(
    val localFileUri: String? = null,
    val remoteUrl: String? = null,
    val localUpdatedAtMs: Long = 0L,
    val remoteUpdatedAtMs: Long = 0L
)

internal object PodcastArtworkMetaCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(workspaceKey: String, entries: Map<String, PodcastArtworkMeta>): String {
        val file = PodcastArtworkMetaFile(
            workspaceKey = workspaceKey,
            savedAtEpochMs = System.currentTimeMillis(),
            entries = entries.mapValues { (_, meta) ->
                StoredPodcastArtworkMeta(
                    localFileUri = meta.localFileUri,
                    remoteUrl = meta.remoteUrl,
                    localUpdatedAtMs = meta.localUpdatedAtMs,
                    remoteUpdatedAtMs = meta.remoteUpdatedAtMs
                )
            }
        )
        return json.encodeToString(file)
    }

    fun decode(raw: String, expectedWorkspaceKey: String): Map<String, PodcastArtworkMeta>? {
        val file = runCatching { json.decodeFromString<PodcastArtworkMetaFile>(raw) }.getOrNull()
            ?: return null
        if (file.workspaceKey != expectedWorkspaceKey) return null
        return file.entries.mapValues { (cacheKey, stored) ->
            PodcastArtworkMeta(
                cacheKey = cacheKey,
                localFileUri = stored.localFileUri,
                remoteUrl = stored.remoteUrl,
                localUpdatedAtMs = stored.localUpdatedAtMs,
                remoteUpdatedAtMs = stored.remoteUpdatedAtMs
            )
        }
    }
}

class PodcastArtworkMetadataStore(private val filesDir: File) {

    suspend fun read(workspaceKey: String): Map<String, PodcastArtworkMeta>? =
        withContext(Dispatchers.IO) {
            val file = metaFile(workspaceKey)
            if (!file.isFile) return@withContext null
            PodcastArtworkMetaCodec.decode(file.readText(), workspaceKey)
        }

    suspend fun write(workspaceKey: String, entries: Map<String, PodcastArtworkMeta>) {
        withContext(Dispatchers.IO) {
            val file = metaFile(workspaceKey)
            file.parentFile?.mkdirs()
            file.writeText(PodcastArtworkMetaCodec.encode(workspaceKey, entries))
        }
    }

    fun artworkDirectory(workspaceKey: String): File =
        File(File(filesDir, ARTWORK_ROOT), workspaceKeySha(workspaceKey))

    fun artworkFile(workspaceKey: String, cacheKey: String, extension: String): File {
        val safeKey = cacheKey.replace('/', '_')
        return File(artworkDirectory(workspaceKey), "$safeKey.$extension")
    }

    private fun metaFile(workspaceKey: String): File = File(
        File(filesDir, CACHE_DIR),
        "podcast_artwork_meta_${workspaceKeySha(workspaceKey)}.json"
    )

    private fun workspaceKeySha(workspaceKey: String): String = podcastImageCacheKey(workspaceKey)

    companion object {
        private const val CACHE_DIR = "cache"
        private const val ARTWORK_ROOT = "podcast-artwork-files"
    }
}

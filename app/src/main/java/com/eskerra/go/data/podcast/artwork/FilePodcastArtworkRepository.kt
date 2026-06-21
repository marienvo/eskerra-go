package com.eskerra.go.data.podcast.artwork

import com.eskerra.go.core.model.PodcastArtworkMeta
import com.eskerra.go.core.podcast.podcastArtworkMemoryKey
import com.eskerra.go.core.podcast.podcastImageCacheKey
import com.eskerra.go.core.podcast.rss.RssChannelArtworkParser
import com.eskerra.go.core.repository.PodcastArtworkRepository
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class FilePodcastArtworkRepository(
    private val filesDir: File,
    private val httpClient: OkHttpClient,
    private val metadataStore: PodcastArtworkMetadataStore = PodcastArtworkMetadataStore(filesDir),
    private val clock: () -> Long = System::currentTimeMillis
) : PodcastArtworkRepository {

    private val memoryUris = ConcurrentHashMap<String, String?>()

    // Inner maps must be ConcurrentHashMap, not LinkedHashMap: artwork resolution fans out
    // across coroutines (catalog priming + the playing episode's artwork job + visible rows),
    // so a single workspace map is mutated and serialized concurrently. A plain LinkedHashMap
    // throws ConcurrentModificationException when encode() iterates it mid-write.
    private val metadataByWorkspace =
        ConcurrentHashMap<String, ConcurrentHashMap<String, PodcastArtworkMeta>>()

    override fun peekMemoryUri(workspaceKey: String, rssFeedUrl: String): String? {
        val memoryKey = podcastArtworkMemoryKey(workspaceKey, rssFeedUrl)
        memoryUris[memoryKey]?.let { return it }
        val cacheKey = podcastImageCacheKey(rssFeedUrl)
        return metadataByWorkspace[workspaceKey]?.get(cacheKey)?.let(::renderableUri)
    }

    override suspend fun loadMetadataFromDisk(workspaceKey: String) {
        val loaded = metadataStore.read(workspaceKey).orEmpty()
        // Merge into the existing map rather than swapping the reference, so a concurrent
        // resolveUri holding the current map doesn't overwrite freshly-loaded entries.
        val target = metadataByWorkspace.getOrPut(workspaceKey) { ConcurrentHashMap() }
        target.putAll(loaded)
        loaded.values.forEach { meta ->
            renderableUri(meta)?.let { uri ->
                memoryUris["$workspaceKey::${meta.cacheKey}"] = uri
            }
        }
    }

    override suspend fun resolveUri(
        workspaceKey: String,
        rssFeedUrl: String,
        fetchRssXml: suspend (String) -> String?,
        allowNetwork: Boolean
    ): String? {
        val trimmed = rssFeedUrl.trim()
        if (trimmed.isEmpty()) return null
        peekMemoryUri(workspaceKey, trimmed)?.let { return it }

        val cacheKey = podcastImageCacheKey(trimmed)
        val memoryKey = podcastArtworkMemoryKey(workspaceKey, trimmed)
        val metaMap = metadataByWorkspace.getOrPut(workspaceKey) { ConcurrentHashMap() }
        val existing = metaMap[cacheKey]
        renderableUri(existing)?.let { uri ->
            memoryUris[memoryKey] = uri
            return uri
        }

        if (!allowNetwork) return null

        // fetchRssXml is backed by a blocking OkHttp call; callers resolve artwork from the main
        // thread (composition + viewModelScope), so force it onto IO or it throws
        // NetworkOnMainThreadException (swallowed downstream) and artwork never resolves.
        val rssXml = withContext(Dispatchers.IO) { fetchRssXml(trimmed) }
            ?: return storeRemoteFallback(
                workspaceKey,
                cacheKey,
                memoryKey,
                metaMap,
                existing?.remoteUrl
            )
        val remoteArtworkUrl = RssChannelArtworkParser.parseArtworkUrl(rssXml)
            ?: return storeRemoteFallback(
                workspaceKey,
                cacheKey,
                memoryKey,
                metaMap,
                existing?.remoteUrl
            )

        val downloaded = downloadArtwork(workspaceKey, cacheKey, remoteArtworkUrl)
        val now = clock()
        val updated = if (downloaded != null) {
            PodcastArtworkMeta(
                cacheKey = cacheKey,
                localFileUri = downloaded,
                remoteUrl = remoteArtworkUrl,
                localUpdatedAtMs = now,
                remoteUpdatedAtMs = now
            )
        } else {
            PodcastArtworkMeta(
                cacheKey = cacheKey,
                localFileUri = existing?.localFileUri,
                remoteUrl = remoteArtworkUrl,
                localUpdatedAtMs = existing?.localUpdatedAtMs ?: 0L,
                remoteUpdatedAtMs = now
            )
        }
        metaMap[cacheKey] = updated
        metadataStore.write(workspaceKey, metaMap.toMap())
        val uri = renderableUri(updated)
        uri?.let { memoryUris[memoryKey] = it }
        return uri
    }

    private suspend fun downloadArtwork(
        workspaceKey: String,
        cacheKey: String,
        url: String
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.bytes() ?: return@withContext null
                if (body.isEmpty()) return@withContext null
                val extension = extensionFor(url, response.header("Content-Type"))
                val target = metadataStore.artworkFile(workspaceKey, cacheKey, extension)
                target.parentFile?.mkdirs()
                target.writeBytes(body)
                target.toURI().toString()
            }
        }.getOrNull()
    }

    private suspend fun storeRemoteFallback(
        workspaceKey: String,
        cacheKey: String,
        memoryKey: String,
        metaMap: MutableMap<String, PodcastArtworkMeta>,
        remoteUrl: String?
    ): String? {
        if (remoteUrl.isNullOrBlank()) return null
        val now = clock()
        val updated = PodcastArtworkMeta(
            cacheKey = cacheKey,
            remoteUrl = remoteUrl,
            remoteUpdatedAtMs = now
        )
        metaMap[cacheKey] = updated
        metadataStore.write(workspaceKey, metaMap.toMap())
        val uri = renderableUri(updated)
        uri?.let { memoryUris[memoryKey] = it }
        return uri
    }

    private fun renderableUri(meta: PodcastArtworkMeta?): String? {
        meta ?: return null
        val now = clock()
        meta.localFileUri?.takeIf { File(URI(it)).isFile }?.let { local ->
            if (now - meta.localUpdatedAtMs <= LOCAL_FILE_TTL_MS) return local
        }
        meta.remoteUrl?.takeIf { it.startsWith("http") }?.let { remote ->
            if (now - meta.remoteUpdatedAtMs <= REMOTE_FALLBACK_TTL_MS) return remote
        }
        return null
    }

    private fun extensionFor(url: String, contentType: String?): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        return when {
            type == "image/png" || url.lowercase().endsWith(".png") -> "png"
            type == "image/webp" || url.lowercase().endsWith(".webp") -> "webp"
            type == "image/gif" || url.lowercase().endsWith(".gif") -> "gif"
            else -> "jpg"
        }
    }

    companion object {
        const val LOCAL_FILE_TTL_MS = 90L * 24L * 60L * 60L * 1_000L
        const val REMOTE_FALLBACK_TTL_MS = 24L * 60L * 60L * 1_000L
        const val DOWNLOAD_TIMEOUT_MS = 10_000L
    }
}

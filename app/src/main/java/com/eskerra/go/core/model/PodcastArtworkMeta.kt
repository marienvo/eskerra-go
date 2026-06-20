package com.eskerra.go.core.model

/**
 * Disk metadata for one RSS feed artwork entry (spec §12.2 / `podcastImageMeta`).
 *
 * [localFileUri] is a `file://` path under app-private storage when downloaded.
 * [remoteUrl] is the last known remote artwork URL (fallback when download fails).
 */
data class PodcastArtworkMeta(
    val cacheKey: String,
    val localFileUri: String? = null,
    val remoteUrl: String? = null,
    val localUpdatedAtMs: Long = 0L,
    val remoteUpdatedAtMs: Long = 0L
)

package com.eskerra.go.core.podcast

/** djb2-style hash of lowercased trimmed RSS URL → `rss-{hex}` (spec §12.1). */
fun podcastImageCacheKey(rssFeedUrl: String): String {
    val normalized = rssFeedUrl.trim().lowercase()
    var hash = 5381L
    for (char in normalized) {
        hash = ((hash shl 5) + hash) + char.code
    }
    return "rss-${(hash and 0xFFFFFFFFL).toString(16)}"
}

fun podcastArtworkMemoryKey(workspaceKey: String, rssFeedUrl: String): String =
    "${workspaceKey.trim()}::${podcastImageCacheKey(rssFeedUrl)}"

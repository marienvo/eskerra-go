package com.eskerra.go.data.podcast.rss

/** Fetches raw RSS XML for a feed URL, returning `null` on any failure. */
fun interface RssFeedFetcher {
    fun fetch(url: String, timeoutMs: Long): String?
}

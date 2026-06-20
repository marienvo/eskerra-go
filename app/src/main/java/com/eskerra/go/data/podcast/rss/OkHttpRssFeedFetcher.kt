package com.eskerra.go.data.podcast.rss

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [RssFeedFetcher] backed by OkHttp. The per-call read/connect timeout comes from
 * the `📻` frontmatter (`timeoutMs`, default 8000). Any non-2xx response, network
 * error, or empty body resolves to `null` so the caller leaves the file unchanged.
 */
class OkHttpRssFeedFetcher(private val baseClient: OkHttpClient = OkHttpClient()) :
    RssFeedFetcher {

    override fun fetch(url: String, timeoutMs: Long): String? {
        val request = runCatching { Request.Builder().url(url).get().build() }.getOrNull()
            ?: return null
        val client = baseClient.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}

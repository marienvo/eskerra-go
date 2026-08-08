package com.eskerra.go.data.share

import com.eskerra.go.core.repository.PageTitleFetcher
import com.eskerra.go.core.share.HtmlCharset
import com.eskerra.go.core.share.HtmlTitleExtractor
import com.eskerra.go.core.share.SharedUrl
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [PageTitleFetcher] over the app's shared OkHttp client, following the same shape as
 * `OkHttpRssFeedFetcher`: a per-call client with tight timeouts, and every failure swallowed.
 *
 * Two guards keep an untrusted shared URL cheap: only textual responses are read at all, and
 * at most [MAX_HTML_BYTES] of the body is buffered — closing the response aborts the rest of the
 * transfer, so a shared link to a huge file costs a connection and nothing more.
 */
class OkHttpPageTitleFetcher(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PageTitleFetcher {

    override suspend fun fetchTitle(url: String): String? = withContext(ioDispatcher) {
        blockingFetchTitle(url)
    }

    private fun blockingFetchTitle(url: String): String? {
        // Re-validated here as well: a file:// or content:// share must never reach the network.
        val safeUrl = SharedUrl.soleUrlOrNull(url) ?: return null
        val request = runCatching {
            Request.Builder()
                .url(safeUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
        }.getOrNull() ?: return null

        val client = baseClient.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val body = response.body ?: return null
                val contentType = body.contentType()
                if (contentType != null &&
                    contentType.type != "text" &&
                    contentType.subtype !in HTML_SUBTYPES
                ) {
                    return null
                }

                val source = body.source()
                source.request(MAX_HTML_BYTES)
                val head = source.buffer.snapshot(
                    minOf(source.buffer.size, MAX_HTML_BYTES).toInt()
                )
                val charset = HtmlCharset.resolve(
                    contentType?.charset()?.name(),
                    head.string(StandardCharsets.US_ASCII)
                )
                HtmlTitleExtractor.extract(head.string(charset))
            }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 4_000L
        const val MAX_HTML_BYTES = 256L * 1024

        private val HTML_SUBTYPES = setOf("html", "xhtml+xml")
    }
}

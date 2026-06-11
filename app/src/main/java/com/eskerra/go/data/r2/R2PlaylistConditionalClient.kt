package com.eskerra.go.data.r2

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.playlist.normalizePlaylistEntryForSync
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

private const val INVALID_STRUCTURE = "R2 playlist.json has an invalid structure."
private const val INVALID_JSON = "R2 playlist.json is not valid JSON."

/** Result of a conditional (ETag) GET against the R2 `playlist.json`. */
sealed interface R2ConditionalResult {
    /** Server returned 304 — the cached etag is still current. */
    data object NotModified : R2ConditionalResult

    /** No object (404) or an empty body. */
    data object Missing : R2ConditionalResult

    /** A newer object, with its current [etag] (may be `null` if absent). */
    data class Updated(val entry: PlaylistEntry, val etag: String?) : R2ConditionalResult
}

/**
 * Conditional GET for the R2 `playlist.json`, mirroring
 * `packages/eskerra-core/src/r2PlaylistConditional.ts`. Sends `If-None-Match`
 * when a prior [etag] exists and maps 304/404/empty to the result variants.
 */
class R2PlaylistConditionalClient(
    private val httpClient: OkHttpClient,
    private val clock: () -> Instant = Instant::now
) {

    fun fetch(config: R2Config, etag: String?): R2ConditionalResult {
        val headers = if (etag != null) mapOf("If-None-Match" to etag) else emptyMap()
        val request = buildSignedPlaylistRequest(config, "GET", clock(), headers = headers)
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 304) return R2ConditionalResult.NotModified
            if (response.code == 404) return R2ConditionalResult.Missing

            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw R2PlaylistException(R2ErrorFormatter.format(R2Verb.READ, response.code, body))
            }
            if (body.isBlank()) return R2ConditionalResult.Missing

            val element = try {
                Json.parseToJsonElement(body)
            } catch (_: SerializationException) {
                throw R2PlaylistException(INVALID_JSON)
            }
            val entry = normalizePlaylistEntryForSync(element)
                ?: throw R2PlaylistException(INVALID_STRUCTURE)
            return R2ConditionalResult.Updated(entry, response.header("ETag"))
        }
    }
}

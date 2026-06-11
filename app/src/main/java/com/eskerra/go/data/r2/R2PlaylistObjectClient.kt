package com.eskerra.go.data.r2

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.playlist.normalizePlaylistEntryForSync
import com.eskerra.go.core.playlist.serializePlaylistEntry
import com.eskerra.go.core.repository.R2PlaylistClient
import java.time.Instant
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

private const val INVALID_STRUCTURE = "R2 playlist.json has an invalid structure."

/**
 * GET / PUT / DELETE for the R2 `playlist.json` object, mirroring
 * `packages/eskerra-core/src/r2PlaylistObject.ts`. All requests are SigV4
 * presigned-query (no `Authorization` header).
 */
class R2PlaylistObjectClient(
    private val httpClient: OkHttpClient,
    private val clock: () -> Instant = Instant::now
) : R2PlaylistClient {

    /** Presigned GET. 404 / empty body → `null`; invalid shape → throws. */
    override fun get(config: R2Config): PlaylistEntry? {
        val request = buildSignedPlaylistRequest(config, "GET", clock())
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw R2PlaylistException(R2ErrorFormatter.format(R2Verb.READ, response.code, body))
            }
            if (body.isBlank()) return null
            val element = Json.parseToJsonElement(body)
            return normalizePlaylistEntryForSync(element)
                ?: throw R2PlaylistException(INVALID_STRUCTURE)
        }
    }

    /** Presigned PUT with `Content-Type: application/json` and serialized body. */
    override fun put(config: R2Config, entry: PlaylistEntry) {
        val body = serializePlaylistEntry(entry).toRequestBody(null)
        val request = buildSignedPlaylistRequest(
            config,
            "PUT",
            clock(),
            headers = mapOf("Content-Type" to "application/json"),
            body = body
        )
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw R2PlaylistException(
                    R2ErrorFormatter.format(R2Verb.WRITE, response.code, text)
                )
            }
        }
    }

    /** Presigned DELETE. 404 is treated as success. */
    override fun delete(config: R2Config) {
        val request = buildSignedPlaylistRequest(config, "DELETE", clock())
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404 || response.isSuccessful) return
            val text = response.body?.string().orEmpty()
            throw R2PlaylistException(R2ErrorFormatter.format(R2Verb.DELETE, response.code, text))
        }
    }
}

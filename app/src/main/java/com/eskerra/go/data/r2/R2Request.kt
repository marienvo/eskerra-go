package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.vault.VaultLayout
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody

internal const val PLAYLIST_OBJECT_KEY = VaultLayout.PLAYLIST_FILE

/**
 * Builds a SigV4 presigned-query [Request] for the vault's `playlist.json`.
 * Extra [headers] are sent unsigned (only `host` is signed), matching the
 * presigned-query contract.
 */
internal fun buildSignedPlaylistRequest(
    config: R2Config,
    method: String,
    timestamp: Instant,
    headers: Map<String, String> = emptyMap(),
    body: RequestBody? = null
): Request {
    val rawUrl = R2ObjectUrl.buildR2ObjectUrl(config, PLAYLIST_OBJECT_KEY).toHttpUrl()
    val signedUrl = SigV4Presigner.presignedQueryUrl(
        method = method,
        url = rawUrl,
        accessKeyId = config.accessKeyId.trim(),
        secretAccessKey = config.secretAccessKey.trim(),
        timestamp = timestamp
    )
    val builder = Request.Builder().url(signedUrl).method(method, body)
    for ((name, value) in headers) builder.header(name, value)
    return builder.build()
}

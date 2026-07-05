package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.core.vault.VaultLayout
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody

internal const val PLAYLIST_OBJECT_KEY = VaultLayout.PLAYLIST_FILE

/** Vault-relative R2 prefix that mirrors the vault root for device-only binaries. */
internal const val BINARIES_PREFIX = VaultLayout.BINARIES_PREFIX

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
): Request = buildSignedObjectRequest(config, method, PLAYLIST_OBJECT_KEY, timestamp, headers, body)

/**
 * Builds a SigV4 presigned-query [Request] for an arbitrary object [objectKey].
 * Extra [headers] are sent unsigned (only `host` is signed).
 */
internal fun buildSignedObjectRequest(
    config: R2Config,
    method: String,
    objectKey: String,
    timestamp: Instant,
    headers: Map<String, String> = emptyMap(),
    body: RequestBody? = null
): Request {
    val rawUrl = R2ObjectUrl.buildR2ObjectUrl(config, objectKey).toHttpUrl()
    return signAndBuild(config, method, rawUrl, timestamp, headers, body)
}

/**
 * Builds a SigV4 presigned-query `ListObjectsV2` [Request] over the bucket root.
 * The query params are added before signing so they are covered by the signature.
 */
internal fun buildSignedListRequest(
    config: R2Config,
    timestamp: Instant,
    prefix: String,
    continuationToken: String? = null
): Request {
    val base = R2Settings.r2S3AccountBaseUrl(config).trimEnd('/')
    val bucket = config.bucket.trim()
    val rawUrl = "$base/$bucket".toHttpUrl().newBuilder()
        .addQueryParameter("list-type", "2")
        .addQueryParameter("prefix", prefix)
        .apply {
            if (continuationToken != null) {
                addQueryParameter("continuation-token", continuationToken)
            }
        }
        .build()
    return signAndBuild(config, "GET", rawUrl, timestamp, emptyMap(), null)
}

private fun signAndBuild(
    config: R2Config,
    method: String,
    rawUrl: okhttp3.HttpUrl,
    timestamp: Instant,
    headers: Map<String, String>,
    body: RequestBody?
): Request {
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

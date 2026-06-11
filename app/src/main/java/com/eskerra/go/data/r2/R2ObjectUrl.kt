package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.vault.R2Settings

/**
 * Builds R2 S3 object URLs, mirroring `buildR2ObjectUrl` from
 * `packages/eskerra-core/src/r2PlaylistObject.ts`:
 *
 * ```
 * base = stripTrailingSlashes(r2S3AccountBaseUrl(config))
 * key  = encodeURIComponent(objectKey).replace(/%2F/g, '/')
 * url  = `${base}/${config.bucket}/${key}`
 * ```
 */
object R2ObjectUrl {

    fun buildR2ObjectUrl(config: R2Config, objectKey: String): String {
        val base = R2Settings.r2S3AccountBaseUrl(config).trimEnd('/')
        val bucket = config.bucket.trim()
        val key = encodeObjectKey(objectKey)
        return "$base/$bucket/$key"
    }

    /** `encodeURIComponent` semantics, but `/` separators are preserved. */
    private fun encodeObjectKey(objectKey: String): String =
        objectKey.split("/").joinToString("/") { encodeUriComponent(it) }

    /**
     * Matches JS `encodeURIComponent`: keeps the unreserved set
     * `A-Za-z0-9 - _ . ! ~ * ' ( )` and percent-encodes everything else as UTF-8.
     */
    private fun encodeUriComponent(value: String): String {
        val builder = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt().toChar()
            if (ch.isUnreservedUriComponent()) {
                builder.append(ch)
            } else {
                builder.append('%')
                builder.append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return builder.toString()
    }

    private fun Char.isUnreservedUriComponent(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-_.!~*'()"
}

package com.eskerra.go.data.r2

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.HttpUrl

/**
 * Hand-rolled AWS SigV4 **presigned-query** signer (region `auto`, service `s3`).
 *
 * Credentials are placed in the query string (`X-Amz-*`), not the `Authorization`
 * header — required because some runtimes drop/alter that header, yielding
 * `SignatureDoesNotMatch` (spec §"S3 signing and HTTP"). The payload is signed as
 * `UNSIGNED-PAYLOAD`; only `host` is a signed header, so callers may attach
 * unsigned request headers (`Content-Type`, `If-None-Match`) freely.
 */
object SigV4Presigner {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    private const val TERMINATOR = "aws4_request"

    private val amzDateFormat =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val dateStampFormat =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    fun presignedQueryUrl(
        method: String,
        url: HttpUrl,
        accessKeyId: String,
        secretAccessKey: String,
        timestamp: Instant,
        region: String = "auto",
        service: String = "s3",
        expiresSeconds: Long = 900L
    ): HttpUrl {
        val amzDate = amzDateFormat.format(timestamp)
        val dateStamp = dateStampFormat.format(timestamp)
        val credentialScope = "$dateStamp/$region/$service/$TERMINATOR"
        val hostHeader = canonicalHost(url)

        val baseParams = linkedMapOf<String, String>()
        for (name in url.queryParameterNames) {
            url.queryParameter(name)?.let { baseParams[name] = it }
        }
        baseParams["X-Amz-Algorithm"] = ALGORITHM
        baseParams["X-Amz-Credential"] = "$accessKeyId/$credentialScope"
        baseParams["X-Amz-Date"] = amzDate
        baseParams["X-Amz-Expires"] = expiresSeconds.toString()
        baseParams["X-Amz-SignedHeaders"] = "host"

        val canonicalQuery = baseParams.entries
            .map { encodeRfc3986(it.key) to encodeRfc3986(it.value) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }

        val canonicalRequest = buildString {
            append(method).append('\n')
            append(url.encodedPath).append('\n')
            append(canonicalQuery).append('\n')
            append("host:").append(hostHeader).append('\n').append('\n')
            append("host").append('\n')
            append(UNSIGNED_PAYLOAD)
        }

        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(amzDate).append('\n')
            append(credentialScope).append('\n')
            append(hex(sha256(canonicalRequest.toByteArray(Charsets.UTF_8))))
        }

        val signingKey = signingKey(secretAccessKey, dateStamp, region, service)
        val signature = hex(hmacSha256(signingKey, stringToSign.toByteArray(Charsets.UTF_8)))

        val builder = url.newBuilder()
        for ((name, value) in baseParams) {
            builder.setEncodedQueryParameter(encodeRfc3986(name), encodeRfc3986(value))
        }
        builder.setEncodedQueryParameter("X-Amz-Signature", signature)
        return builder.build()
    }

    /** Host with an explicit port only when it is non-default for the scheme. */
    private fun canonicalHost(url: HttpUrl): String {
        val isDefaultPort = (url.scheme == "https" && url.port == 443) ||
            (url.scheme == "http" && url.port == 80)
        return if (isDefaultPort) url.host else "${url.host}:${url.port}"
    }

    internal fun signingKey(
        secretAccessKey: String,
        dateStamp: String,
        region: String,
        service: String
    ): ByteArray {
        val kDate = hmacSha256(
            "AWS4$secretAccessKey".toByteArray(Charsets.UTF_8),
            dateStamp.toByteArray(Charsets.UTF_8)
        )
        val kRegion = hmacSha256(kDate, region.toByteArray(Charsets.UTF_8))
        val kService = hmacSha256(kRegion, service.toByteArray(Charsets.UTF_8))
        return hmacSha256(kService, TERMINATOR.toByteArray(Charsets.UTF_8))
    }

    /** Strict RFC 3986 encoding (canonical query): keep only `A-Za-z0-9-_.~`. */
    private fun encodeRfc3986(value: String): String {
        val builder = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt().toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.~") {
                builder.append(ch)
            } else {
                builder.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return builder.toString()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) builder.append("%02x".format(byte.toInt() and 0xFF))
        return builder.toString()
    }
}

package com.eskerra.go.data.r2

import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class SigV4PresignerTest {

    private val accessKeyId = "AKIDEXAMPLE"
    private val secret = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
    private val timestamp = Instant.parse("2023-01-01T00:00:00Z")

    /**
     * Known-answer vector computed independently with Python `hmac`/`hashlib`
     * (region `auto`, service `s3`, presigned query, UNSIGNED-PAYLOAD).
     */
    @Test
    fun `presigned query signature matches reference vector`() {
        val url = "http://localhost:8080/mock-bucket/playlist.json".toHttpUrl()

        val signed = SigV4Presigner.presignedQueryUrl(
            method = "GET",
            url = url,
            accessKeyId = accessKeyId,
            secretAccessKey = secret,
            timestamp = timestamp
        )

        assertEquals(
            "ffd921c364d0f941399e6434e1e844d4bb5259b9801aa32787bf0707b34c50ae",
            signed.queryParameter("X-Amz-Signature")
        )
    }

    @Test
    fun `presigned query carries the expected amz parameters`() {
        val url = "http://localhost:8080/mock-bucket/playlist.json".toHttpUrl()

        val signed = SigV4Presigner.presignedQueryUrl(
            method = "GET",
            url = url,
            accessKeyId = accessKeyId,
            secretAccessKey = secret,
            timestamp = timestamp
        )

        assertEquals("AWS4-HMAC-SHA256", signed.queryParameter("X-Amz-Algorithm"))
        assertEquals(
            "AKIDEXAMPLE/20230101/auto/s3/aws4_request",
            signed.queryParameter("X-Amz-Credential")
        )
        assertEquals("20230101T000000Z", signed.queryParameter("X-Amz-Date"))
        assertEquals("900", signed.queryParameter("X-Amz-Expires"))
        assertEquals("host", signed.queryParameter("X-Amz-SignedHeaders"))
    }

    @Test
    fun `signing key derivation is deterministic`() {
        val key = SigV4Presigner.signingKey(secret, "20230101", "auto", "s3")
        // Re-deriving yields identical bytes.
        val again = SigV4Presigner.signingKey(secret, "20230101", "auto", "s3")
        assertEquals(key.toList(), again.toList())
    }
}

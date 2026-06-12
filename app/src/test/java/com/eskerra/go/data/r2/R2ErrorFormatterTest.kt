package com.eskerra.go.data.r2

import org.junit.Assert.assertEquals
import org.junit.Test

class R2ErrorFormatterTest {

    @Test
    fun `formats status without an xml code`() {
        assertEquals(
            "R2 GET playlist.json failed: HTTP 500",
            R2ErrorFormatter.format(R2Verb.READ, 500, "internal error")
        )
    }

    @Test
    fun `formats status with an xml code but no hint`() {
        val body = "<Error><Code>NoSuchBucket</Code></Error>"
        assertEquals(
            "R2 PUT playlist.json failed: HTTP 404 (NoSuchBucket)",
            R2ErrorFormatter.format(R2Verb.WRITE, 404, body)
        )
    }

    @Test
    fun `appends read hint with eu note on access denied`() {
        val body = "<Error><Code>AccessDenied</Code></Error>"
        assertEquals(
            "R2 GET playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Read on the R2 S3 API token for this bucket " +
                "(Cloudflare: R2 → Manage R2 API Tokens). " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            R2ErrorFormatter.format(R2Verb.READ, 403, body)
        )
    }

    @Test
    fun `appends write hint on access denied`() {
        val body = "<Error><Code>AccessDenied</Code></Error>"
        assertEquals(
            "R2 PUT playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Write on the R2 S3 API token for this bucket. " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            R2ErrorFormatter.format(R2Verb.WRITE, 403, body)
        )
    }

    @Test
    fun `appends delete hint on access denied`() {
        val body = "<Error><Code>AccessDenied</Code></Error>"
        assertEquals(
            "R2 DELETE playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Delete on the R2 S3 API token for this bucket. " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            R2ErrorFormatter.format(R2Verb.DELETE, 403, body)
        )
    }
}

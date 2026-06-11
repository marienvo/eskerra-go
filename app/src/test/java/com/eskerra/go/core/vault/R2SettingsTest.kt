package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2SettingsTest {

    // ── isVaultR2PlaylistConfigured ──────────────────────────────────────────

    @Test
    fun `configured when all four fields non-empty`() {
        assertTrue(R2Settings.isVaultR2PlaylistConfigured(settingsWithR2()))
    }

    @Test
    fun `not configured when r2 absent`() {
        assertFalse(R2Settings.isVaultR2PlaylistConfigured(EskerraSettings()))
    }

    @Test
    fun `not configured when any field empty`() {
        assertFalse(
            R2Settings.isVaultR2PlaylistConfigured(
                settingsWithR2().copy(r2 = r2().copy(bucket = ""))
            )
        )
        assertFalse(
            R2Settings.isVaultR2PlaylistConfigured(
                settingsWithR2().copy(r2 = r2().copy(endpoint = ""))
            )
        )
        assertFalse(
            R2Settings.isVaultR2PlaylistConfigured(
                settingsWithR2().copy(r2 = r2().copy(accessKeyId = ""))
            )
        )
        assertFalse(
            R2Settings.isVaultR2PlaylistConfigured(
                settingsWithR2().copy(r2 = r2().copy(secretAccessKey = ""))
            )
        )
    }

    @Test
    fun `not configured when fields only whitespace`() {
        assertFalse(
            R2Settings.isVaultR2PlaylistConfigured(
                settingsWithR2().copy(r2 = r2().copy(bucket = "  "))
            )
        )
    }

    // ── effectiveR2Endpoint ──────────────────────────────────────────────────

    @Test
    fun `default jurisdiction passes endpoint through unchanged`() {
        val cfg = r2(jurisdiction = R2Jurisdiction.Default)
        assertEquals("https://abc.r2.cloudflarestorage.com", R2Settings.effectiveR2Endpoint(cfg))
    }

    @Test
    fun `eu jurisdiction inserts eu subdomain`() {
        val cfg = r2(jurisdiction = R2Jurisdiction.Eu)
        assertEquals(
            "https://abc.eu.r2.cloudflarestorage.com",
            R2Settings.effectiveR2Endpoint(cfg)
        )
    }

    @Test
    fun `fedramp jurisdiction inserts fedramp subdomain`() {
        val cfg = r2(jurisdiction = R2Jurisdiction.Fedramp)
        assertEquals(
            "https://abc.fedramp.r2.cloudflarestorage.com",
            R2Settings.effectiveR2Endpoint(cfg)
        )
    }

    @Test
    fun `already-correct eu hostname not double-rewritten`() {
        val cfg = r2(
            endpoint = "https://abc.eu.r2.cloudflarestorage.com",
            jurisdiction = R2Jurisdiction.Eu
        )
        assertEquals(
            "https://abc.eu.r2.cloudflarestorage.com",
            R2Settings.effectiveR2Endpoint(cfg)
        )
    }

    @Test
    fun `endpoint with trailing slash is trimmed`() {
        val cfg = r2(endpoint = "https://abc.r2.cloudflarestorage.com/")
        assertEquals("https://abc.r2.cloudflarestorage.com", R2Settings.effectiveR2Endpoint(cfg))
    }

    // ── r2S3AccountBaseUrl ───────────────────────────────────────────────────

    @Test
    fun `base url without bucket suffix returned unchanged`() {
        val cfg = r2(endpoint = "https://abc.r2.cloudflarestorage.com", bucket = "my-bucket")
        assertEquals("https://abc.r2.cloudflarestorage.com", R2Settings.r2S3AccountBaseUrl(cfg))
    }

    @Test
    fun `bucket suffix is stripped when pasted as full S3 api url`() {
        val cfg = r2(
            endpoint = "https://abc.r2.cloudflarestorage.com/my-bucket",
            bucket = "my-bucket"
        )
        assertEquals("https://abc.r2.cloudflarestorage.com", R2Settings.r2S3AccountBaseUrl(cfg))
    }

    // helpers

    private fun r2(
        endpoint: String = "https://abc.r2.cloudflarestorage.com",
        bucket: String = "my-bucket",
        accessKeyId: String = "keyId",
        secretAccessKey: String = "secret",
        jurisdiction: R2Jurisdiction = R2Jurisdiction.Default
    ) = R2Config(
        endpoint = endpoint,
        bucket = bucket,
        accessKeyId = accessKeyId,
        secretAccessKey = secretAccessKey,
        jurisdiction = jurisdiction
    )

    private fun settingsWithR2() = EskerraSettings(r2 = r2())
}

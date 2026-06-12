package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import com.eskerra.go.core.model.VaultSettingsException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormTest {

    @Test
    fun `all fields empty produces settings with no r2`() {
        val result = build()
        val settings = result.getOrThrow()
        assertNull(settings.r2)
    }

    @Test
    fun `all fields filled produces r2 config`() {
        val result = build(
            r2Endpoint = "https://abc.r2.cloudflarestorage.com",
            r2Bucket = "bucket",
            r2AccessKeyId = "key",
            r2SecretAccessKey = "secret"
        )
        val r2 = result.getOrThrow().r2!!
        assertEquals("https://abc.r2.cloudflarestorage.com", r2.endpoint)
        assertEquals("bucket", r2.bucket)
    }

    @Test
    fun `partial fill returns failure with exact message`() {
        val result = build(r2Endpoint = "https://abc.r2.cloudflarestorage.com")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as VaultSettingsException
        assertEquals("Complete all Cloudflare R2 fields or clear them all.", ex.error.message)
    }

    @Test
    fun `whitespace-only fields count as empty`() {
        val result = build(
            r2Endpoint = "  https://abc.r2.cloudflarestorage.com  ",
            r2Bucket = "  ",
            r2AccessKeyId = "key",
            r2SecretAccessKey = "secret"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `fields are trimmed in the produced config`() {
        val result = build(
            r2Endpoint = "  https://abc.r2.cloudflarestorage.com  ",
            r2Bucket = " bucket ",
            r2AccessKeyId = " key ",
            r2SecretAccessKey = " secret "
        )
        val r2 = result.getOrThrow().r2!!
        assertEquals("https://abc.r2.cloudflarestorage.com", r2.endpoint)
        assertEquals("bucket", r2.bucket)
        assertEquals("key", r2.accessKeyId)
        assertEquals("secret", r2.secretAccessKey)
    }

    @Test
    fun `jurisdiction is passed through to config`() {
        val result = build(
            r2Endpoint = "https://abc.r2.cloudflarestorage.com",
            r2Jurisdiction = R2Jurisdiction.Eu,
            r2Bucket = "b",
            r2AccessKeyId = "k",
            r2SecretAccessKey = "s"
        )
        assertEquals(R2Jurisdiction.Eu, result.getOrThrow().r2!!.jurisdiction)
    }

    @Test
    fun `previousShared extras are preserved`() {
        val previous = EskerraSettings(
            extras = mapOf("themePreference" to JsonPrimitive("dark"))
        )
        val result = build(previousShared = previous)
        val extras = result.getOrThrow().extras
        assertTrue(extras.containsKey("themePreference"))
    }

    @Test
    fun `clear r2 with previousShared preserves extras`() {
        val previous = EskerraSettings(
            r2 = R2Config("https://e.com", "b", "k", "s"),
            extras = mapOf("frontmatterProperties" to JsonPrimitive("[]"))
        )
        val result = build(previousShared = previous)
        val settings = result.getOrThrow()
        assertNull(settings.r2)
        assertTrue(settings.extras.containsKey("frontmatterProperties"))
    }

    private fun build(
        r2Endpoint: String = "",
        r2Jurisdiction: R2Jurisdiction = R2Jurisdiction.Default,
        r2Bucket: String = "",
        r2AccessKeyId: String = "",
        r2SecretAccessKey: String = "",
        previousShared: EskerraSettings? = null
    ) = buildEskerraSettingsFromForm(
        r2Endpoint = r2Endpoint,
        r2Jurisdiction = r2Jurisdiction,
        r2Bucket = r2Bucket,
        r2AccessKeyId = r2AccessKeyId,
        r2SecretAccessKey = r2SecretAccessKey,
        previousShared = previousShared
    )
}

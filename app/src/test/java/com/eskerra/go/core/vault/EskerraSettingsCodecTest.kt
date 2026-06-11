package com.eskerra.go.core.vault

import com.eskerra.go.core.model.EskerraSettings
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.model.R2Jurisdiction
import com.eskerra.go.core.model.VaultSettingsException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EskerraSettingsCodecTest {

    private fun buildR2Json(jurisdiction: String? = null): String {
        val jur = if (jurisdiction != null) ""","jurisdiction":"$jurisdiction"""" else ""
        return """{"r2":{"endpoint":"https://a.r2.cloudflarestorage.com",""" +
            """"bucket":"b","accessKeyId":"k","secretAccessKey":"s"$jur}}"""
    }

    @Test
    fun `parse empty object returns settings with no r2`() {
        val result = EskerraSettingsCodec.parse("{}")
        val settings = result.getOrThrow()
        assertNull(settings.r2)
        assertTrue(settings.extras.isEmpty())
    }

    @Test
    fun `parse full r2 block`() {
        val json = """
            {
              "r2": {
                "endpoint": "https://abc.r2.cloudflarestorage.com",
                "bucket": "my-bucket",
                "accessKeyId": "key123",
                "secretAccessKey": "secret456"
              }
            }
        """.trimIndent()
        val r2 = EskerraSettingsCodec.parse(json).getOrThrow().r2!!
        assertEquals("https://abc.r2.cloudflarestorage.com", r2.endpoint)
        assertEquals("my-bucket", r2.bucket)
        assertEquals("key123", r2.accessKeyId)
        assertEquals("secret456", r2.secretAccessKey)
        assertEquals(R2Jurisdiction.Default, r2.jurisdiction)
    }

    @Test
    fun `parse r2 with eu jurisdiction`() {
        val json = buildR2Json(jurisdiction = "eu")
        val r2 = EskerraSettingsCodec.parse(json).getOrThrow().r2!!
        assertEquals(R2Jurisdiction.Eu, r2.jurisdiction)
    }

    @Test
    fun `parse r2 with fedramp jurisdiction`() {
        val json = buildR2Json(jurisdiction = "fedramp")
        val r2 = EskerraSettingsCodec.parse(json).getOrThrow().r2!!
        assertEquals(R2Jurisdiction.Fedramp, r2.jurisdiction)
    }

    @Test
    fun `parse preserves unknown desktop-only keys as extras`() {
        val json = """
            {
              "themePreference": {"mode": "dark"},
              "frontmatterProperties": [],
              "linkSnippetBlockedDomains": ["ads.example.com"]
            }
        """.trimIndent()
        val settings = EskerraSettingsCodec.parse(json).getOrThrow()
        assertNull(settings.r2)
        assertNotNull(settings.extras["themePreference"])
        assertNotNull(settings.extras["frontmatterProperties"])
        assertNotNull(settings.extras["linkSnippetBlockedDomains"])
    }

    @Test
    fun `parse legacy displayName is excluded from extras (ignored key)`() {
        val json = """{"displayName": "Alice", "r2": null}"""
        val settings = EskerraSettingsCodec.parse(json).getOrThrow()
        assertTrue(!settings.extras.containsKey("displayName"))
    }

    @Test
    fun `parse invalid json returns failure with exact message`() {
        val result = EskerraSettingsCodec.parse("not-json")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as VaultSettingsException
        assertEquals("settings-shared.json has an invalid structure.", ex.error.message)
    }

    @Test
    fun `parse invalid r2 field type returns failure`() {
        val json = """{"r2": "not-an-object"}"""
        val result = EskerraSettingsCodec.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `serialize empty settings produces empty object with trailing newline`() {
        val output = EskerraSettingsCodec.serialize(EskerraSettings())
        assertEquals("{}\n", output)
    }

    @Test
    fun `serialize r2 with default jurisdiction omits jurisdiction key`() {
        val settings = EskerraSettings(
            r2 = R2Config(
                endpoint = "https://abc.r2.cloudflarestorage.com",
                bucket = "my-bucket",
                accessKeyId = "key",
                secretAccessKey = "secret",
                jurisdiction = R2Jurisdiction.Default
            )
        )
        val output = EskerraSettingsCodec.serialize(settings)
        assertTrue(output.endsWith("\n"))
        assertTrue(!output.contains("jurisdiction"))
        assertTrue(output.contains("\"endpoint\""))
    }

    @Test
    fun `serialize r2 with eu jurisdiction includes jurisdiction`() {
        val settings = EskerraSettings(
            r2 = R2Config(
                endpoint = "https://abc.eu.r2.cloudflarestorage.com",
                bucket = "b",
                accessKeyId = "k",
                secretAccessKey = "s",
                jurisdiction = R2Jurisdiction.Eu
            )
        )
        val output = EskerraSettingsCodec.serialize(settings)
        assertTrue(output.contains("\"jurisdiction\": \"eu\""))
    }

    @Test
    fun `serialize preserves extras round-trip`() {
        val json = """
            {
              "themePreference": {"mode":"dark"},
              "r2": {
                "endpoint": "https://abc.r2.cloudflarestorage.com",
                "bucket": "b",
                "accessKeyId": "k",
                "secretAccessKey": "s"
              }
            }
        """.trimIndent()
        val settings = EskerraSettingsCodec.parse(json).getOrThrow()
        val output = EskerraSettingsCodec.serialize(settings)
        assertTrue(output.contains("themePreference"))
        assertTrue(output.contains("\"bucket\": \"b\""))
    }

    @Test
    fun `serialize uses 2-space indent`() {
        val settings = EskerraSettings(
            r2 = R2Config("https://abc.r2.cloudflarestorage.com", "b", "k", "s"),
            extras = mapOf("extra" to JsonPrimitive("val"))
        )
        val output = EskerraSettingsCodec.serialize(settings)
        assertTrue(output.contains("  \"r2\""))
    }

    @Test
    fun `round-trip with extras preserves all data`() {
        val json = """
            {
              "r2": {
                "endpoint": "https://abc.r2.cloudflarestorage.com",
                "bucket": "bucket",
                "accessKeyId": "id",
                "secretAccessKey": "sec",
                "jurisdiction": "eu"
              },
              "themePreference": {"mode": "dark"}
            }
        """.trimIndent()
        val parsed = EskerraSettingsCodec.parse(json).getOrThrow()
        val output = EskerraSettingsCodec.serialize(parsed)
        val reparsed = EskerraSettingsCodec.parse(output).getOrThrow()

        assertEquals(parsed.r2, reparsed.r2)
        assertEquals(parsed.extras.keys, reparsed.extras.keys)
    }
}

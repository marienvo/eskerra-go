package com.eskerra.go.data.r2

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.playlist.serializePlaylistEntry
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class R2PlaylistObjectClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: R2PlaylistObjectClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = R2PlaylistObjectClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = R2Config(
        endpoint = server.url("/").toString(),
        bucket = "mock-bucket",
        accessKeyId = "key",
        secretAccessKey = "secret"
    )

    private val validBody = """
        {"episodeId":"e1","mp3Url":"http://x/a.mp3","positionMs":1000,
         "durationMs":5000,"updatedAt":42,"playbackOwnerId":"dev","controlRevision":3}
    """.trimIndent()

    @Test
    fun `get parses a valid playlist object`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validBody))

        val entry = client.get(config())

        assertEquals(
            PlaylistEntry("e1", "http://x/a.mp3", 1000, 5000, 42, "dev", 3),
            entry
        )
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/mock-bucket/playlist.json", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `get returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client.get(config()))
    }

    @Test
    fun `get returns null on empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("   "))
        assertNull(client.get(config()))
    }

    @Test
    fun `get throws on invalid structure`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val ex = assertThrows(R2PlaylistException::class.java) { client.get(config()) }
        assertEquals("R2 playlist.json has an invalid structure.", ex.message)
    }

    @Test
    fun `get throws formatted access denied error`() {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("<Error><Code>AccessDenied</Code></Error>")
        )
        val ex = assertThrows(R2PlaylistException::class.java) { client.get(config()) }
        assertEquals(
            "R2 GET playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Read on the R2 S3 API token for this bucket " +
                "(Cloudflare: R2 → Manage R2 API Tokens). " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            ex.message
        )
    }

    @Test
    fun `put sends json content type and serialized body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val entry = PlaylistEntry("e1", "http://x/a.mp3", 1000, 5000, 42, "dev", 3)

        client.put(config(), entry)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals(serializePlaylistEntry(entry), recorded.body.readUtf8())
    }

    @Test
    fun `put throws formatted write error`() {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("<Error><Code>AccessDenied</Code></Error>")
        )
        val entry = PlaylistEntry("e1", "http://x/a.mp3", 1000, 5000, 42, "dev", 3)
        val ex = assertThrows(R2PlaylistException::class.java) { client.put(config(), entry) }
        assertEquals(
            "R2 PUT playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Write on the R2 S3 API token for this bucket. " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            ex.message
        )
    }

    @Test
    fun `delete succeeds on 204`() {
        server.enqueue(MockResponse().setResponseCode(204))
        client.delete(config())
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `delete treats 404 as success`() {
        server.enqueue(MockResponse().setResponseCode(404))
        client.delete(config())
    }

    @Test
    fun `delete throws formatted delete error`() {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("<Error><Code>AccessDenied</Code></Error>")
        )
        val ex = assertThrows(R2PlaylistException::class.java) { client.delete(config()) }
        assertEquals(
            "R2 DELETE playlist.json failed: HTTP 403 (AccessDenied). " +
                "Grant Object Delete on the R2 S3 API token for this bucket. " +
                "EU data location buckets need jurisdiction \"EU\" in settings " +
                "(or the .eu.r2.cloudflarestorage.com endpoint).",
            ex.message
        )
    }
}

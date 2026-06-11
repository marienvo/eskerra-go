package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class R2PlaylistConditionalClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: R2PlaylistConditionalClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = R2PlaylistConditionalClient(OkHttpClient())
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

    private val validBody =
        """{"episodeId":"e1","mp3Url":"http://x/a.mp3","positionMs":1000,"durationMs":null}"""

    @Test
    fun `fetch returns updated with etag`() {
        server.enqueue(
            MockResponse().setResponseCode(200).addHeader("ETag", "etag123").setBody(validBody)
        )

        val result = client.fetch(config(), etag = null)

        result as R2ConditionalResult.Updated
        assertEquals("e1", result.entry.episodeId)
        assertEquals("etag123", result.etag)
    }

    @Test
    fun `fetch sends if-none-match when etag present`() {
        server.enqueue(MockResponse().setResponseCode(304))

        client.fetch(config(), etag = "prev-etag")

        assertEquals("prev-etag", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `fetch maps 304 to not modified`() {
        server.enqueue(MockResponse().setResponseCode(304))
        assertTrue(client.fetch(config(), "x") is R2ConditionalResult.NotModified)
    }

    @Test
    fun `fetch maps 404 to missing`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(client.fetch(config(), null) is R2ConditionalResult.Missing)
    }

    @Test
    fun `fetch maps empty body to missing`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        assertTrue(client.fetch(config(), null) is R2ConditionalResult.Missing)
    }

    @Test
    fun `fetch throws on invalid json`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json{"))
        val ex = assertThrows(R2PlaylistException::class.java) { client.fetch(config(), null) }
        assertEquals("R2 playlist.json is not valid JSON.", ex.message)
    }

    @Test
    fun `fetch throws on invalid structure`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val ex = assertThrows(R2PlaylistException::class.java) { client.fetch(config(), null) }
        assertEquals("R2 playlist.json has an invalid structure.", ex.message)
    }
}

package com.eskerra.go.data.share

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpPageTitleFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun fetcher(timeoutMs: Long = 1_000L) = OkHttpPageTitleFetcher(
        baseClient = OkHttpClient(),
        timeoutMs = timeoutMs,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun url(path: String = "/page") = server.url(path).toString()

    @Test
    fun readsTitleFromHtmlResponse() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><head><title>Kotlin coroutines guide</title></head></html>")
        )

        assertEquals("Kotlin coroutines guide", fetcher().fetchTitle(url()))
    }

    @Test
    fun decodesLatin1FromHeaderCharset() = runTest {
        val body = Buffer().writeString(
            "<html><head><title>Café Ruë</title></head>",
            StandardCharsets.ISO_8859_1
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=ISO-8859-1").setBody(body)
        )

        assertEquals("Café Ruë", fetcher().fetchTitle(url()))
    }

    @Test
    fun decodesLatin1FromMetaCharset() = runTest {
        val body = Buffer().writeString(
            """<html><head><meta charset="ISO-8859-1"><title>Café</title></head>""",
            StandardCharsets.ISO_8859_1
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(body))

        assertEquals("Café", fetcher().fetchTitle(url()))
    }

    @Test
    fun returnsNullOnNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<title>Nope</title>"))

        assertNull(fetcher().fetchTitle(url()))
    }

    @Test
    fun ignoresNonTextContentTypes() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/pdf").setBody("<title>x</title>")
        )

        assertNull(fetcher().fetchTitle(url()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun readsTitleThatSitsInsideTheReadCap() = runTest {
        val padding = "<!-- ${"a".repeat(300_000)} -->"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<head><title>Early title</title>$padding</head>")
        )

        assertEquals("Early title", fetcher().fetchTitle(url()))
    }

    @Test
    fun ignoresTitleBeyondTheReadCap() = runTest {
        val padding = "<!-- ${"a".repeat(400_000)} -->"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<head>$padding<title>Late title</title></head>")
        )

        assertNull(fetcher().fetchTitle(url()))
    }

    @Test
    fun returnsNullOnTimeout() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<title>Slow</title>")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )

        assertNull(fetcher(timeoutMs = 200L).fetchTitle(url()))
    }

    @Test
    fun returnsNullWhenTheServerNeverResponds() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertNull(fetcher(timeoutMs = 200L).fetchTitle(url()))
    }

    @Test
    fun followsRedirects() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", server.url("/final"))
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html").setBody("<title>Final</title>")
        )

        assertEquals("Final", fetcher().fetchTitle(url()))
    }

    @Test
    fun returnsNullWhenTheConnectionFails() = runTest {
        val dead = url()
        server.shutdown()

        assertNull(fetcher(timeoutMs = 200L).fetchTitle(dead))
    }

    @Test
    fun refusesNonHttpSchemes() = runTest {
        assertNull(fetcher().fetchTitle("file:///etc/passwd"))
        assertEquals(0, server.requestCount)
    }
}

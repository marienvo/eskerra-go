package com.eskerra.go.data.podcast.artwork

import com.eskerra.go.core.model.PodcastArtworkMeta
import com.eskerra.go.core.podcast.podcastImageCacheKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePodcastArtworkRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var repository: FilePodcastArtworkRepository
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        server = MockWebServer()
        server.start()
        repository = FilePodcastArtworkRepository(filesDir, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolveUri_downloadsArtworkAndCachesLocally() = runTest {
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(imageBytes))
        )
        val feedUrl = server.url("/feed.xml").toString()
        val rssXml = """
            <?xml version="1.0"?>
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <title>Demo</title>
                <itunes:image href="${server.url("/cover.png")}"/>
              </channel>
            </rss>
        """.trimIndent()

        val uri = repository.resolveUri(
            workspaceKey = "vault-a",
            rssFeedUrl = feedUrl,
            fetchRssXml = { rssXml },
            allowNetwork = true
        )

        assertNotNull(uri)
        assertTrue(uri!!.startsWith("file:"))
        assertEquals(uri, repository.peekMemoryUri("vault-a", feedUrl))
        val cacheKey = podcastImageCacheKey(feedUrl)
        val imageFile = PodcastArtworkMetadataStore(filesDir)
            .artworkFile("vault-a", cacheKey, "png")
        assertTrue(imageFile.isFile)
    }

    @Test
    fun resolveUri_underConcurrentLoad_doesNotThrowConcurrentModification() {
        // Refresh-while-playing fans out artwork resolution across coroutines that all share
        // one workspace metadata map. Before the fix the inner map was a LinkedHashMap, so a
        // write() iterating it while another resolve mutated it crashed with
        // ConcurrentModificationException (PodcastArtworkMetaCodec.encode). Hammer that race.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(png))
        }

        runBlocking {
            (0 until 64).map { i ->
                async(Dispatchers.Default) {
                    val feedUrl = server.url("/feed$i.xml").toString()
                    val rssXml = """
                        <?xml version="1.0"?>
                        <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
                          <channel>
                            <title>Demo $i</title>
                            <itunes:image href="${server.url("/cover$i.png")}"/>
                          </channel>
                        </rss>
                    """.trimIndent()
                    repeat(4) {
                        repository.resolveUri(
                            workspaceKey = "vault-a",
                            rssFeedUrl = feedUrl,
                            fetchRssXml = { rssXml },
                            allowNetwork = true
                        )
                        // Concurrent disk reload merges into the same shared map.
                        repository.loadMetadataFromDisk("vault-a")
                    }
                }
            }.awaitAll()
        }
    }

    @Test
    fun resolveUri_nonHttpArtworkUrl_doesNotCrashOrCacheNull() = runTest {
        val feedUrl = "https://example.com/feed.xml"
        val rssXml = """
            <?xml version="1.0"?>
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <title>Demo</title>
                <itunes:image href="/relative/cover.png"/>
              </channel>
            </rss>
        """.trimIndent()

        val uri = repository.resolveUri(
            workspaceKey = "vault-a",
            rssFeedUrl = feedUrl,
            fetchRssXml = { rssXml },
            allowNetwork = true
        )

        assertEquals(null, uri)
        assertEquals(null, repository.peekMemoryUri("vault-a", feedUrl))
    }

    @Test
    fun loadMetadataFromDisk_hydratesMemoryAfterRestart() = runTest {
        val cacheKey = podcastImageCacheKey("https://example.com/feed")
        val localUri = File(filesDir, "cached.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }.toURI().toString()
        val metaStore = PodcastArtworkMetadataStore(filesDir)
        metaStore.write(
            "vault-a",
            mapOf(
                cacheKey to PodcastArtworkMeta(
                    cacheKey = cacheKey,
                    localFileUri = localUri,
                    localUpdatedAtMs = System.currentTimeMillis()
                )
            )
        )

        val freshRepo = FilePodcastArtworkRepository(filesDir, OkHttpClient())
        freshRepo.loadMetadataFromDisk("vault-a")

        assertEquals(localUri, freshRepo.peekMemoryUri("vault-a", "https://example.com/feed"))
    }

    @Test
    fun resolveUri_afterMetadataLoad_returnsLocalUriAtNinetyDayBoundary() = runTest {
        val now = 1_000_000_000_000L
        val feedUrl = "https://example.com/feed"
        val cacheKey = podcastImageCacheKey(feedUrl)
        val localUri = File(filesDir, "cached.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }.toURI().toString()
        PodcastArtworkMetadataStore(filesDir).write(
            "vault-a",
            mapOf(
                cacheKey to PodcastArtworkMeta(
                    cacheKey = cacheKey,
                    localFileUri = localUri,
                    localUpdatedAtMs = now - FilePodcastArtworkRepository.LOCAL_FILE_TTL_MS
                )
            )
        )

        val freshRepo = FilePodcastArtworkRepository(
            filesDir = filesDir,
            httpClient = OkHttpClient(),
            clock = { now }
        )
        freshRepo.loadMetadataFromDisk("vault-a")

        val uri = freshRepo.resolveUri(
            workspaceKey = "vault-a",
            rssFeedUrl = feedUrl,
            fetchRssXml = { error("Network should not be used") },
            allowNetwork = false
        )

        assertEquals(localUri, uri)
    }

    @Test
    fun resolveUri_afterMetadataLoad_ignoresLocalUriAfterNinetyDays() = runTest {
        val now = 1_000_000_000_000L
        val feedUrl = "https://example.com/feed"
        val cacheKey = podcastImageCacheKey(feedUrl)
        val localUri = File(filesDir, "cached.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }.toURI().toString()
        PodcastArtworkMetadataStore(filesDir).write(
            "vault-a",
            mapOf(
                cacheKey to PodcastArtworkMeta(
                    cacheKey = cacheKey,
                    localFileUri = localUri,
                    localUpdatedAtMs = now - FilePodcastArtworkRepository.LOCAL_FILE_TTL_MS - 1L
                )
            )
        )

        val freshRepo = FilePodcastArtworkRepository(
            filesDir = filesDir,
            httpClient = OkHttpClient(),
            clock = { now }
        )
        freshRepo.loadMetadataFromDisk("vault-a")

        val uri = freshRepo.resolveUri(
            workspaceKey = "vault-a",
            rssFeedUrl = feedUrl,
            fetchRssXml = { error("Network should not be used") },
            allowNetwork = false
        )

        assertEquals(null, uri)
    }
}

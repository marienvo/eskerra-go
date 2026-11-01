package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2Config
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class R2DesktopActivityRepositoryTest {
    @Test
    fun readsNewestValidPresenceAcrossInstallations() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                "<ListBucketResult><IsTruncated>false</IsTruncated>" +
                    "<Contents><Key>presence/desktop/a.json</Key>" +
                    "<Size>1</Size><ETag>e</ETag></Contents>" +
                    "<Contents><Key>presence/desktop/b.json</Key>" +
                    "<Size>1</Size><ETag>e</ETag></Contents>" +
                    "</ListBucketResult>"
            )
        )
        server.enqueue(MockResponse().setBody(presence("2026-11-01T12:00:00Z")))
        server.enqueue(MockResponse().setBody(presence("2026-11-01T12:04:00Z")))
        server.start()
        try {
            val repository = R2DesktopActivityRepository(R2BinaryObjectClient(OkHttpClient()))
            val config = R2Config(server.url("/").toString(), "bucket", "key", "secret")
            assertEquals(
                Instant.parse("2026-11-01T12:04:00Z").toEpochMilli(),
                repository.latestActivityAtEpochMillis(config)
            )
            assertEquals(
                "presence/desktop/",
                server.takeRequest().requestUrl?.queryParameter("prefix")
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun ignoresMalformedPresence() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                "<ListBucketResult><IsTruncated>false</IsTruncated>" +
                    "<Contents><Key>presence/desktop/a.json</Key>" +
                    "<Size>1</Size><ETag>e</ETag></Contents>" +
                    "</ListBucketResult>"
            )
        )
        server.enqueue(MockResponse().setBody("not json"))
        server.start()
        try {
            val repository = R2DesktopActivityRepository(R2BinaryObjectClient(OkHttpClient()))
            val config = R2Config(server.url("/").toString(), "bucket", "key", "secret")
            assertEquals(null, repository.latestActivityAtEpochMillis(config))
        } finally {
            server.shutdown()
        }
    }

    private fun presence(timestamp: String) =
        """{"version":1,"installationId":"id","lastActivityAt":"$timestamp"}"""
}

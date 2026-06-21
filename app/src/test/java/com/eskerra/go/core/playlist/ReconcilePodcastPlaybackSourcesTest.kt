package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastNativeSessionSnapshot
import com.eskerra.go.core.model.PodcastPlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilePodcastPlaybackSourcesTest {

    private val episode = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = "https://cdn/episode.mp3",
        isListened = false,
        mp3Url = "https://cdn/episode.mp3",
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily News",
        sourceFile = "2026 News - podcasts.md",
        title = "Episode title"
    )
    private val catalog = PodcastCatalog(
        allEpisodes = listOf(episode),
        sections = emptyList()
    )

    @Test
    fun nativeSession_winsOverLocalSnapshot() {
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = PodcastPlaybackSnapshot(
                episodeId = episode.id,
                mp3Url = episode.mp3Url,
                positionMs = 12_000L,
                durationMs = 60_000L,
                updatedAtMs = 1L
            ),
            remoteEntry = PlaylistEntry(
                episodeId = episode.id,
                mp3Url = episode.mp3Url,
                positionMs = 15_000L,
                durationMs = 60_000L
            ),
            nativeSession = PodcastNativeSessionSnapshot(
                episodeId = episode.id,
                positionMs = 30_000L,
                durationMs = 60_000L,
                isPlaying = true
            )
        )

        assertEquals(30_000L, hydration?.positionMs)
    }

    @Test
    fun localSnapshot_usedWhenNativeMissing() {
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = PodcastPlaybackSnapshot(
                episodeId = episode.id,
                mp3Url = episode.mp3Url,
                positionMs = 12_000L,
                durationMs = 60_000L,
                updatedAtMs = 1L
            ),
            remoteEntry = null,
            nativeSession = null
        )

        assertEquals(12_000L, hydration?.positionMs)
    }

    @Test
    fun remoteEntry_usedWhenLocalMissing() {
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = null,
            remoteEntry = PlaylistEntry(
                episodeId = episode.id,
                mp3Url = episode.mp3Url,
                positionMs = 18_000L,
                durationMs = 60_000L
            ),
            nativeSession = null
        )

        assertEquals(18_000L, hydration?.positionMs)
    }

    @Test
    fun ignoresNativeSessionWithoutMeaningfulProgress() {
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = PodcastPlaybackSnapshot(
                episodeId = episode.id,
                mp3Url = episode.mp3Url,
                positionMs = 12_000L,
                durationMs = 60_000L,
                updatedAtMs = 1L
            ),
            remoteEntry = null,
            nativeSession = PodcastNativeSessionSnapshot(
                episodeId = episode.id,
                positionMs = 500L,
                durationMs = 60_000L,
                isPlaying = false
            )
        )

        assertEquals(12_000L, hydration?.positionMs)
    }

    @Test
    fun hasResumablePlayback_trueWhenAnySourceMatches() {
        assertTrue(
            hasResumablePodcastPlayback(
                catalog = catalog,
                localSnapshot = PodcastPlaybackSnapshot(
                    episodeId = episode.id,
                    mp3Url = episode.mp3Url,
                    positionMs = 12_000L,
                    durationMs = 60_000L,
                    updatedAtMs = 1L
                ),
                remoteEntry = null,
                nativeSession = null
            )
        )
    }

    @Test
    fun returnsNullWhenEpisodeIsListened() {
        val listened = episode.copy(isListened = true)
        val listenedCatalog = catalog.copy(allEpisodes = listOf(listened))

        assertNull(
            reconcilePodcastPlaybackSources(
                catalog = listenedCatalog,
                localSnapshot = PodcastPlaybackSnapshot(
                    episodeId = listened.id,
                    mp3Url = listened.mp3Url,
                    positionMs = 12_000L,
                    durationMs = 60_000L,
                    updatedAtMs = 1L
                ),
                remoteEntry = null,
                nativeSession = null
            )
        )
    }
}

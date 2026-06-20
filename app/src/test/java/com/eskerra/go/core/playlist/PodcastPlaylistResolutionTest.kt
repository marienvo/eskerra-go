package com.eskerra.go.core.playlist

import com.eskerra.go.core.model.PlaylistEntry
import com.eskerra.go.core.model.PodcastCatalog
import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastPlaylistResolutionTest {

    @Test
    fun resolveHydration_returnsEpisodeAndPositionForUnplayedMatch() {
        val episode = sampleEpisode(isListened = false)
        val catalog = catalogOf(episode)
        val entry = PlaylistEntry(
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            positionMs = 12_000L,
            durationMs = 60_000L
        )

        val hydration = resolvePodcastPlaylistHydration(catalog, entry)

        assertEquals(episode.id, hydration?.episode?.id)
        assertEquals(12_000L, hydration?.positionMs)
    }

    @Test
    fun resolveHydration_returnsNullWhenEpisodeAlreadyListened() {
        val episode = sampleEpisode(isListened = true)
        val catalog = catalogOf(episode)
        val entry = PlaylistEntry(
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            positionMs = 1_000L,
            durationMs = 60_000L
        )

        assertNull(resolvePodcastPlaylistHydration(catalog, entry))
    }

    @Test
    fun shouldClearPlaylist_whenEpisodeMissingOrListened() {
        val played = sampleEpisode(isListened = true)
        val catalog = catalogOf(played)
        val entry = PlaylistEntry(
            episodeId = played.id,
            mp3Url = played.mp3Url,
            positionMs = 1_000L,
            durationMs = 60_000L
        )

        assertTrue(shouldClearPlaylistForCatalog(catalog, entry))
        assertTrue(
            shouldClearPlaylistForCatalog(
                PodcastCatalog(emptyList(), emptyList()),
                entry
            )
        )
    }

    @Test
    fun shouldNotClearPlaylist_whenEpisodeIsUnplayed() {
        val episode = sampleEpisode(isListened = false)
        val catalog = catalogOf(episode)
        val entry = PlaylistEntry(
            episodeId = episode.id,
            mp3Url = episode.mp3Url,
            positionMs = 1_000L,
            durationMs = 60_000L
        )

        assertFalse(shouldClearPlaylistForCatalog(catalog, entry))
    }

    private fun catalogOf(episode: PodcastEpisode) = PodcastCatalog(
        allEpisodes = listOf(episode),
        sections = listOf(
            PodcastSection(episodes = listOf(episode), rssFeedUrl = null, title = "News")
        )
    )

    private fun sampleEpisode(isListened: Boolean) = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = "https://cdn/episode.mp3",
        isListened = isListened,
        mp3Url = "https://cdn/episode.mp3",
        rssFeedUrl = null,
        sectionTitle = "News",
        seriesName = "Daily News",
        sourceFile = "2026 News - podcasts.md",
        title = "Episode title"
    )
}

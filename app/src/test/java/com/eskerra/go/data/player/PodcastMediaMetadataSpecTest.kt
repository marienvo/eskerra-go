package com.eskerra.go.data.player

import com.eskerra.go.core.model.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastMediaMetadataSpecTest {

    @Test
    fun podcastMediaMetadataSpec_mapsEpisodeTitleSeriesAndArtwork() {
        val spec = podcastMediaMetadataSpec(
            episode = sampleEpisode(),
            artworkUri = " file:///tmp/artwork.png "
        )

        assertEquals("Episode title", spec.title)
        assertEquals("Daily News", spec.artist)
        assertEquals("file:///tmp/artwork.png", spec.artworkUri)
    }

    @Test
    fun podcastMediaMetadataSpec_dropsBlankArtworkUri() {
        val spec = podcastMediaMetadataSpec(
            episode = sampleEpisode(),
            artworkUri = " "
        )

        assertNull(spec.artworkUri)
    }

    private fun sampleEpisode() = PodcastEpisode(
        articleUrl = null,
        date = "2026-03-15",
        id = "https://cdn/episode.mp3",
        isListened = false,
        mp3Url = "https://cdn/episode.mp3",
        rssFeedUrl = "https://feeds.example/daily.xml",
        sectionTitle = "News",
        seriesName = "Daily News",
        sourceFile = "2026 News - podcasts.md",
        title = "Episode title"
    )
}

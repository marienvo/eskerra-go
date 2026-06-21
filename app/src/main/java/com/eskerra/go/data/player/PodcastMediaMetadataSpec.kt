package com.eskerra.go.data.player

import com.eskerra.go.core.model.PodcastEpisode

internal data class PodcastMediaMetadataSpec(
    val title: String,
    val artist: String,
    val artworkUri: String?
)

internal fun podcastMediaMetadataSpec(
    episode: PodcastEpisode,
    artworkUri: String?
): PodcastMediaMetadataSpec = PodcastMediaMetadataSpec(
    title = episode.title,
    artist = episode.seriesName,
    artworkUri = artworkUri?.trim()?.takeIf { it.isNotEmpty() }
)

package com.eskerra.go.core.model

/** UI-ready group of unplayed podcast episodes for one vault section. */
data class PodcastSection(
    val episodes: List<PodcastEpisode>,
    val rssFeedUrl: String?,
    val title: String
)

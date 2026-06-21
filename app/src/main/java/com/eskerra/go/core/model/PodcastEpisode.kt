package com.eskerra.go.core.model

/** Parsed episode row from a `YYYY Section - podcasts.md` stub file. */
data class PodcastEpisode(
    val articleUrl: String?,
    val date: String,
    val id: String,
    val isListened: Boolean,
    val mp3Url: String,
    val rssFeedUrl: String?,
    val sectionTitle: String,
    val seriesName: String,
    val sourceFile: String,
    val title: String
)

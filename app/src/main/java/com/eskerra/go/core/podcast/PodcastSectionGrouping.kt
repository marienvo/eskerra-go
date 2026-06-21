package com.eskerra.go.core.podcast

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.model.PodcastSection

fun buildPodcastSections(episodes: List<PodcastEpisode>): List<PodcastSection> =
    groupPodcastEpisodesBySection(episodes.filterNot { it.isListened })

fun groupPodcastEpisodesBySection(episodes: List<PodcastEpisode>): List<PodcastSection> = episodes
    .groupBy { it.sectionTitle }
    .map { (title, sectionEpisodes) ->
        PodcastSection(
            episodes = sectionEpisodes.sortedByDescending { it.date },
            rssFeedUrl = sectionEpisodes.firstNotNullOfOrNull { it.rssFeedUrl },
            title = title
        )
    }
    .sortedBy { it.title }

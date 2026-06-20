package com.eskerra.go.core.model

/** Phase-1 podcast catalog: all parsed episodes plus unplayed UI sections. */
data class PodcastCatalog(val allEpisodes: List<PodcastEpisode>, val sections: List<PodcastSection>)

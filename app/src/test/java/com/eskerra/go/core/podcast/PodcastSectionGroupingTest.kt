package com.eskerra.go.core.podcast

import com.eskerra.go.core.model.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSectionGroupingTest {

    @Test
    fun `buildPodcastSections keeps unplayed episodes only and sorts sections and episodes`() {
        val sections = buildPodcastSections(
            listOf(
                episode(title = "Old", date = "2026-03-14", sectionTitle = "News"),
                episode(
                    title = "Played",
                    date = "2026-03-18",
                    sectionTitle = "News",
                    listened = true
                ),
                episode(title = "New", date = "2026-03-16", sectionTitle = "News"),
                episode(title = "Work", date = "2026-03-15", sectionTitle = "Work")
            )
        )

        assertEquals(listOf("News", "Work"), sections.map { it.title })
        assertEquals(listOf("New", "Old"), sections.first().episodes.map { it.title })
    }

    @Test
    fun `groupPodcastEpisodesBySection preserves provided played episodes`() {
        val sections = groupPodcastEpisodesBySection(
            listOf(episode(title = "Played", listened = true))
        )

        assertEquals(listOf("Played"), sections.single().episodes.map { it.title })
    }

    private fun episode(
        title: String,
        date: String = "2026-03-15",
        sectionTitle: String = "News",
        listened: Boolean = false
    ): PodcastEpisode = PodcastEpisode(
        articleUrl = null,
        date = date,
        id = "https://cdn/$title.mp3",
        isListened = listened,
        mp3Url = "https://cdn/$title.mp3",
        rssFeedUrl = null,
        sectionTitle = sectionTitle,
        seriesName = sectionTitle,
        sourceFile = "2026 $sectionTitle - podcasts.md",
        title = title
    )
}

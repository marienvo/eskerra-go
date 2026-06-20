package com.eskerra.go.core.podcast.rss

import java.time.ZoneId

/**
 * Rebuilds the body of a `📻` markdown file from refreshed feed episodes:
 * an H1 show title followed by day-grouped bullets, newest day first. Episodes
 * older than [daysAgo] local-calendar days are omitted (spec §7.3.1).
 */
object RssMarkdownComposer {

    private const val ARTICLE_ICON = "🌐"
    private const val PLAY_TRIANGLE = "▶"

    fun compose(
        showTitle: String,
        episodes: List<RssEpisode>,
        daysAgo: Int,
        nowMs: Long,
        zoneId: ZoneId
    ): String {
        val retained = episodes.filter { episode ->
            val diff = RssCalendar.daysFromTargetToReference(episode.date, nowMs, zoneId)
            diff != null && diff <= daysAgo
        }

        val byDate = retained
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })

        return buildString {
            append("# ").append(showTitle).append('\n')
            byDate.forEach { (date, dayEpisodes) ->
                append('\n').append("## ").append(date).append('\n')
                dayEpisodes
                    .sortedByDescending { it.pubInstant }
                    .forEach { episode -> append(bullet(episode)).append('\n') }
            }
        }
    }

    private fun bullet(episode: RssEpisode): String {
        val mp3 = PodcastMarkdownLinks.sanitizeUrl(episode.mp3Url)
        val article = episode.articleUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { "[$ARTICLE_ICON](${PodcastMarkdownLinks.sanitizeUrl(it)}) " }
            .orEmpty()
        return "- $article${episode.title} [$PLAY_TRIANGLE]($mp3)"
    }
}

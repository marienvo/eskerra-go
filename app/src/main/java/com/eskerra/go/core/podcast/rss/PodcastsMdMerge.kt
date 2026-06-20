package com.eskerra.go.core.podcast.rss

import com.eskerra.go.core.podcast.PodcastFileDetails
import com.eskerra.go.core.podcast.parsePodcastEpisodeLine
import java.time.ZoneId

/**
 * Merges refreshed `📻` feed episodes into a `YYYY Section - podcasts.md` stub,
 * applying the retention, dedupe, collision, and sort rules of spec §7.3.1.
 *
 * The stub's non-episode prefix (everything before the first task bullet) is
 * preserved verbatim; episode lines are fully regenerated from the merged set.
 */
object PodcastsMdMerge {

    private const val PLAY_TRIANGLE = "▶"
    private const val ARTICLE_ICON = "🌐"
    private val TASK_BULLET = Regex("""^\s*-\s*\[[ xX]]\s""")
    private val PARSE_DETAILS = PodcastFileDetails("merge.md", "", 0)

    private data class MergeEpisode(
        val date: String,
        val title: String,
        val mp3Url: String,
        val seriesName: String,
        val articleUrl: String?,
        val played: Boolean
    )

    fun merge(
        existingContent: String,
        candidates: List<RssEpisode>,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val lines = existingContent.replace("\r\n", "\n").split("\n")
        val firstBullet = lines.indexOfFirst { TASK_BULLET.containsMatchIn(it) }
        val prefixLines = if (firstBullet < 0) lines else lines.subList(0, firstBullet)

        val retainedExisting = lines
            .mapNotNull { line -> parsePodcastEpisodeLine(line, PARSE_DETAILS) }
            .map { episode ->
                MergeEpisode(
                    date = episode.date,
                    title = episode.title,
                    mp3Url = episode.mp3Url,
                    seriesName = episode.seriesName,
                    articleUrl = episode.articleUrl,
                    played = episode.isListened
                )
            }
            .filter { keepExisting(it.date, it.played, nowMs, zoneId) }

        val newCandidates = candidates
            .filter { candidateInWindow(it.date, nowMs, zoneId) }
            .map { candidate ->
                MergeEpisode(
                    date = candidate.date,
                    title = candidate.title,
                    mp3Url = candidate.mp3Url,
                    seriesName = candidate.seriesName,
                    articleUrl = candidate.articleUrl,
                    played = false
                )
            }

        val merged = LinkedHashMap<String, MergeEpisode>()
        (retainedExisting + newCandidates).forEach { episode ->
            val key = dedupeKey(episode)
            val existing = merged[key]
            merged[key] = if (existing == null) episode else resolveCollision(existing, episode)
        }

        val episodeLines = merged.values
            .sortedWith(
                compareByDescending<MergeEpisode> { it.date }
                    .thenBy { PodcastMarkdownLinks.sanitizeUrl(it.mp3Url).lowercase() }
            )
            .map(::serialize)

        return (prefixLines + episodeLines).joinToString("\n").let {
            if (it.isEmpty()) it else it.trimEnd('\n') + "\n"
        }
    }

    private fun keepExisting(date: String, played: Boolean, nowMs: Long, zoneId: ZoneId): Boolean {
        val diff = RssCalendar.daysFromTargetToReference(date, nowMs, zoneId) ?: return false
        return when {
            diff <= 1 -> true // today, yesterday, or future
            diff <= 7 -> !played
            else -> false
        }
    }

    private fun candidateInWindow(date: String, nowMs: Long, zoneId: ZoneId): Boolean {
        val diff = RssCalendar.daysFromTargetToReference(date, nowMs, zoneId) ?: return false
        return diff == 0 || diff == 1
    }

    private fun dedupeKey(episode: MergeEpisode): String =
        "${episode.date}|${PodcastMarkdownLinks.normalizeTitleKey(episode.title)}"

    private fun resolveCollision(existing: MergeEpisode, incoming: MergeEpisode): MergeEpisode {
        val played = existing.played || incoming.played
        val existingHasEntity = PodcastMarkdownLinks.hasAmpEntity(existing.mp3Url)
        val incomingHasEntity = PodcastMarkdownLinks.hasAmpEntity(incoming.mp3Url)
        val keepExistingMp3 = when {
            existingHasEntity && !incomingHasEntity -> false
            !existingHasEntity && incomingHasEntity -> true
            else -> true
        }
        return if (keepExistingMp3) {
            existing.copy(
                played = played,
                articleUrl = existing.articleUrl ?: incoming.articleUrl
            )
        } else {
            incoming.copy(
                played = played,
                articleUrl = incoming.articleUrl ?: existing.articleUrl
            )
        }
    }

    private fun serialize(episode: MergeEpisode): String {
        val checkbox = if (episode.played) "x" else " "
        val article = episode.articleUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { "[$ARTICLE_ICON](${PodcastMarkdownLinks.sanitizeUrl(it)}) " }
            .orEmpty()
        val mp3 = PodcastMarkdownLinks.sanitizeUrl(episode.mp3Url)
        return "- [$checkbox] ${episode.date}; $article${episode.title} " +
            "[$PLAY_TRIANGLE]($mp3) (${episode.seriesName})"
    }
}

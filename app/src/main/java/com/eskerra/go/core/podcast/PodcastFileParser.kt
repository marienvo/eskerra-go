package com.eskerra.go.core.podcast

import com.eskerra.go.core.model.PodcastEpisode

data class PodcastFileDetails(val fileName: String, val sectionTitle: String, val year: Int)

data class PodcastMarkdownFile(val fileName: String, val content: String)

data class PodcastCatalogParseResult(val allEpisodes: List<PodcastEpisode>)

fun parsePodcastFileDetails(fileName: String, currentYear: Int): PodcastFileDetails? {
    val baseName = fileName.substringAfterLast('/').substringAfterLast('\\')
    val match = PODCAST_STUB_FILE_REGEX.matchEntire(baseName) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    if (year != currentYear && year != currentYear + 1) return null
    return PodcastFileDetails(
        fileName = baseName,
        sectionTitle = match.groupValues[2].trim(),
        year = year
    )
}

fun isPodcastEpisodesFile(fileName: String, currentYear: Int): Boolean =
    parsePodcastFileDetails(fileName, currentYear) != null

fun parsePodcastFile(fileName: String, content: String, currentYear: Int): List<PodcastEpisode> {
    val details = parsePodcastFileDetails(fileName, currentYear) ?: return emptyList()
    return content
        .lineSequence()
        .mapNotNull { line -> parsePodcastEpisodeLine(line, details) }
        .toList()
}

fun parsePodcastFiles(
    files: List<PodcastMarkdownFile>,
    currentYear: Int
): PodcastCatalogParseResult {
    val episodesById = LinkedHashMap<String, PodcastEpisode>()
    files.forEach { file ->
        parsePodcastFile(file.fileName, file.content, currentYear).forEach { episode ->
            episodesById.putIfAbsent(episode.id, episode)
        }
    }
    return PodcastCatalogParseResult(
        allEpisodes = episodesById.values.sortedByDescending { it.date }
    )
}

fun parsePodcastEpisodeLine(line: String, details: PodcastFileDetails): PodcastEpisode? {
    val match = EPISODE_LINE_REGEX.matchEntire(line) ?: return null
    val listenedToken = match.groupValues[1]
    val date = match.groupValues[2]
    var remainder = match.groupValues[3].trim()

    val articleMatch = ARTICLE_LINK_REGEX.find(remainder)
    val articleUrl = articleMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    if (articleMatch != null && articleMatch.range.first == 0) {
        remainder = remainder.substring(articleMatch.range.last + 1).trimStart()
    }

    val playMatch = PLAY_LINK_REGEX.findAll(remainder).lastOrNull() ?: return null
    val mp3Url = playMatch.groupValues[1].takeIf { it.isNotBlank() } ?: return null
    val title = remainder.substring(0, playMatch.range.first).trim().takeIf { it.isNotBlank() }
        ?: return null
    val seriesTail = remainder.substring(playMatch.range.last + 1)
    val seriesMatch = SERIES_TAIL_REGEX.matchEntire(seriesTail) ?: return null
    val seriesName = seriesMatch.groupValues[1].trim().takeIf { it.isNotBlank() } ?: return null

    return PodcastEpisode(
        articleUrl = articleUrl,
        date = date,
        id = mp3Url,
        isListened = listenedToken.equals("x", ignoreCase = true),
        mp3Url = mp3Url,
        rssFeedUrl = null,
        sectionTitle = details.sectionTitle,
        seriesName = seriesName,
        sourceFile = details.fileName,
        title = title
    )
}

private val PODCAST_STUB_FILE_REGEX =
    Regex("""^(\d{4})\s+(.+?)\s+-\s+podcasts\.md$""", RegexOption.IGNORE_CASE)
private val EPISODE_LINE_REGEX =
    Regex("""^\s*-\s*\[([ xX])]\s+(\d{4}-\d{2}-\d{2});\s*(.+)$""")
private val SERIES_TAIL_REGEX = Regex("""^\s*\(([^()]*)\)\s*$""")

private val articleIcon = String(Character.toChars(0x1F310))
private const val PLAY_TRIANGLE = "\u25B6"
private const val VARIATION_SELECTOR = "\uFE0F"

private val ARTICLE_LINK_REGEX =
    Regex("""^\[${Regex.escape(articleIcon)}]\(([^)]*)\)\s*""")
private val playLinkPattern =
    """\[${Regex.escape(PLAY_TRIANGLE)}""" +
        """(?:${Regex.escape(VARIATION_SELECTOR)})?]\(([^)]*)\)"""
private val PLAY_LINK_REGEX = Regex(playLinkPattern)

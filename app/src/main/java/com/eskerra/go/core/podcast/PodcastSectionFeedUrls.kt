package com.eskerra.go.core.podcast

import com.eskerra.go.core.podcast.rss.PodcastMarkdownLinks
import com.eskerra.go.core.podcast.rss.PodcastRssFileSync
import com.eskerra.go.core.podcast.rss.RssFrontmatterParser
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

private val RSS_FILE_PREFIX = String(Character.toChars(0x1F4FB))

/**
 * Resolves HTTP RSS feed URLs per podcast *series* (show), keyed by a normalized
 * series title. Lets each episode display its own show's artwork instead of falling
 * back to one section-wide feed when a section bundles several shows.
 *
 * Scans every `📻` feed file under [generalDir]; the series title is the feed file's
 * H1 (or filename stem), which matches the `(series)` tail written into episode lines.
 */
fun resolveSeriesFeedUrls(generalDir: File): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    generalDir.listFiles().orEmpty()
        .filter {
            isRegularFile(it) &&
                it.name.startsWith(RSS_FILE_PREFIX) &&
                it.name.endsWith(".md", ignoreCase = true)
        }
        .sortedBy { it.name }
        .forEach { rssFile ->
            val document = RssFrontmatterParser.parse(rssFile.readText(Charsets.UTF_8))
            val feedUrl = document.frontmatter.feedUrls.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val title = PodcastRssFileSync.extractRssPodcastTitle(rssFile.name, document.body)
            result.putIfAbsent(PodcastMarkdownLinks.normalizeTitleKey(title), feedUrl)
        }
    return result
}

/**
 * Resolves HTTP RSS feed URLs for podcast sections from the vault layout:
 * stub → hub → `📻` frontmatter `rssFeedUrl`.
 */
fun resolveSectionFeedUrls(generalDir: File, year: Int): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    generalDir.listFiles().orEmpty()
        .filter { isRegularFile(it) && parsePodcastFileDetails(it.name, year) != null }
        .sortedBy { it.name }
        .forEach { stub ->
            val details = parsePodcastFileDetails(stub.name, year) ?: return@forEach
            val hubFile = File(generalDir, "${details.year} ${details.sectionTitle}.md")
            if (!isRegularFile(hubFile)) return@forEach
            val hubContent = hubFile.readText(Charsets.UTF_8)
            val feedUrl = resolveFeedUrlFromHub(generalDir, hubContent) ?: return@forEach
            result[details.sectionTitle] = feedUrl
        }
    return result
}

private fun resolveFeedUrlFromHub(generalDir: File, hubContent: String): String? {
    for (rssName in PodcastHubRssLinks.parseUncheckedRssLinkNames(hubContent)) {
        val rssFile = File(generalDir, rssName)
        if (!isRegularFile(rssFile)) continue
        val feedUrl = RssFrontmatterParser.parse(rssFile.readText(Charsets.UTF_8))
            .frontmatter.feedUrls.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (feedUrl != null) return feedUrl
    }
    return null
}

private fun isRegularFile(file: File): Boolean =
    Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

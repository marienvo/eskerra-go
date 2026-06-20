package com.eskerra.go.core.podcast

import com.eskerra.go.core.podcast.rss.RssFrontmatterParser
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

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

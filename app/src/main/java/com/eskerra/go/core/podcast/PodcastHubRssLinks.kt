package com.eskerra.go.core.podcast

/** Parses unchecked `📻` wiki-links from a section hub markdown file. */
object PodcastHubRssLinks {
    private val rssPrefix = String(Character.toChars(0x1F4FB))
    private val uncheckedRssLink = Regex("""^\s*-\s*\[ ]\s*\[\[([^\]]+)]]""")

    fun parseUncheckedRssLinkNames(hubContent: String): List<String> = hubContent.lineSequence()
        .mapNotNull { line -> uncheckedRssLink.find(line)?.groupValues?.get(1)?.trim() }
        .filter { it.startsWith(rssPrefix) }
        .map { if (it.endsWith(".md", ignoreCase = true)) it else "$it.md" }
        .toList()
}

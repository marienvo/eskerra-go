package com.eskerra.go.core.podcast.rss

import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** A single feed `<item>` before show/date enrichment. */
data class RawRssItem(
    val title: String,
    val mp3Url: String,
    val pubInstant: Long,
    val articleUrl: String?
)

/**
 * Lenient RSS 2.0 parser. Extracts audio enclosures into [RawRssItem]s, skipping
 * items without an audio URL or a parseable publish date. Parsing is best-effort:
 * malformed XML yields an empty list rather than throwing.
 */
object RssFeedParser {

    private val PUB_DATE_FORMATS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm zzz", java.util.Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
    )

    fun parse(xml: String): List<RawRssItem> {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching {
                    setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false
                    )
                }
                runCatching { isExpandEntityReferences = false }
            }
            factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return emptyList()

        val itemNodes = document.getElementsByTagName("item")
        val items = mutableListOf<RawRssItem>()
        for (i in 0 until itemNodes.length) {
            val element = itemNodes.item(i) as? Element ?: continue
            val mp3Url = audioEnclosureUrl(element) ?: continue
            val pubInstant = parsePubDate(childText(element, "pubDate")) ?: continue
            val title = childText(element, "title")?.trim().orEmpty()
            items += RawRssItem(
                title = title,
                mp3Url = mp3Url.trim(),
                pubInstant = pubInstant,
                articleUrl = childText(element, "link")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        return items
    }

    private fun audioEnclosureUrl(item: Element): String? {
        val enclosures = item.getElementsByTagName("enclosure")
        for (i in 0 until enclosures.length) {
            val enclosure = enclosures.item(i) as? Element ?: continue
            val url = enclosure.getAttribute("url").trim()
            if (url.isEmpty()) continue
            val type = enclosure.getAttribute("type").lowercase()
            if (type.isEmpty() || type.startsWith("audio")) return url
        }
        return null
    }

    private fun childText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node?.parentNode === parent) return node.textContent
        }
        return null
    }

    private fun parsePubDate(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (format in PUB_DATE_FORMATS) {
            val parsed = runCatching {
                ZonedDateTime.parse(value, format).toInstant().toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return runCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** Local-calendar ISO date (`YYYY-MM-DD`) for an epoch-millis instant. */
    fun isoDateOf(epochMillis: Long, zoneId: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toString()
}

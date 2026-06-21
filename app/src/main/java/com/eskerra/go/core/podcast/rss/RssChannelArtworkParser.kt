package com.eskerra.go.core.podcast.rss

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Channel-level artwork URL from RSS 2.0 / iTunes extensions (spec §12.2 step 3). */
object RssChannelArtworkParser {

    fun parseArtworkUrl(xml: String): String? {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
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
        }.getOrNull() ?: return null

        val channel = document.getElementsByTagName("channel").item(0) as? Element ?: return null
        itunesImageHref(channel)?.let { return it }
        imageUrl(channel)?.let { return it }
        return null
    }

    private fun itunesImageHref(channel: Element): String? {
        val byLocalName = channel.getElementsByTagName("itunes:image")
        for (index in 0 until byLocalName.length) {
            val element = byLocalName.item(index) as? Element ?: continue
            val href = element.getAttribute("href").trim()
            if (href.isNotEmpty()) return href
        }
        val byNamespace = channel.getElementsByTagNameNS(ITUNES_NS, "image")
        for (index in 0 until byNamespace.length) {
            val element = byNamespace.item(index) as? Element ?: continue
            val href = element.getAttribute("href").trim()
            if (href.isNotEmpty()) return href
        }
        return null
    }

    private fun imageUrl(channel: Element): String? {
        val imageNodes = channel.getElementsByTagName("image")
        if (imageNodes.length == 0) return null
        val image = imageNodes.item(0) as? Element ?: return null
        val urlNodes = image.getElementsByTagName("url")
        if (urlNodes.length == 0) return null
        return urlNodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"
}

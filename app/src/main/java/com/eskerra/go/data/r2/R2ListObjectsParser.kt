package com.eskerra.go.data.r2

import com.eskerra.go.core.model.R2BinaryObject
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** One page of an S3 `ListObjectsV2` response. */
internal data class R2ListPage(
    val objects: List<R2BinaryObject>,
    val isTruncated: Boolean,
    val nextContinuationToken: String?
)

/**
 * Parses an S3 `ListObjectsV2` XML body (`ListBucketResult`). Uses
 * `javax.xml.parsers` (available on both the JVM and Android) so it stays
 * unit-testable without instrumentation.
 */
internal object R2ListObjectsParser {

    fun parse(xml: String): R2ListPage {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val root = document.documentElement

        val contents = document.getElementsByTagName("Contents")
        val objects = (0 until contents.length).mapNotNull { index ->
            val element = contents.item(index) as? Element ?: return@mapNotNull null
            val key = childText(element, "Key") ?: return@mapNotNull null
            val size = childText(element, "Size")?.toLongOrNull() ?: 0L
            val etag = unquoteEtag(childText(element, "ETag").orEmpty())
            R2BinaryObject(key = key, size = size, etag = etag)
        }

        val truncated = childText(root, "IsTruncated")?.equals("true", ignoreCase = true) == true
        val token = childText(root, "NextContinuationToken")?.takeIf { it.isNotBlank() }
        return R2ListPage(objects = objects, isTruncated = truncated, nextContinuationToken = token)
    }

    /** Direct-child text (avoids grabbing nested elements' text). */
    private fun childText(parent: Element, tag: String): String? {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.tagName == tag) {
                return node.textContent?.trim()
            }
        }
        return null
    }

    /** S3 ETags are wrapped in quotes (`"abc"`); strip a single surrounding pair. */
    private fun unquoteEtag(raw: String): String = raw.trim().removeSurrounding("\"")
}

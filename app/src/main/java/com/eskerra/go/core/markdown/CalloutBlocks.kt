package com.eskerra.go.core.markdown

/**
 * Splits a markdown body into plain-markdown runs and Obsidian callout blocks, so the renderer can
 * draw callouts with a custom card and hand the rest to the standard markdown renderer.
 *
 * A callout block is a maximal run of blockquote lines whose first line is a callout header
 * ([CalloutHeader.matchCalloutHeader]). The header's trailing text becomes the title; the remaining
 * quoted lines (leading `>` stripped) become the callout body markdown.
 */
object CalloutBlocks {

    sealed interface Segment {
        /** A run of standard markdown to render with the library. */
        data class Markdown(val text: String) : Segment

        /** A callout block with resolved metadata and inner body markdown. */
        data class Callout(
            val resolved: CalloutHeader.ResolvedCallout,
            val title: String,
            val body: String
        ) : Segment
    }

    private val BLOCKQUOTE_LINE = Regex("""^\s*>""")
    private val STRIP_QUOTE_MARKER = Regex("""^\s*>\s?""")

    fun segment(body: String): List<Segment> {
        val lines = body.replace("\r\n", "\n").split("\n")
        val segments = mutableListOf<Segment>()
        val pending = StringBuilder()
        var i = 0

        fun flushPending() {
            if (pending.isNotEmpty()) {
                segments += Segment.Markdown(pending.toString())
                pending.setLength(0)
            }
        }

        while (i < lines.size) {
            val header = CalloutHeader.matchCalloutHeader(lines[i])
            if (header == null) {
                if (pending.isNotEmpty()) {
                    pending.append('\n')
                }
                pending.append(lines[i])
                i += 1
                continue
            }

            flushPending()
            var j = i + 1
            while (j < lines.size && BLOCKQUOTE_LINE.containsMatchIn(lines[j])) {
                j += 1
            }
            val bodyLines = lines.subList(i + 1, j).map { it.replaceFirst(STRIP_QUOTE_MARKER, "") }
            segments += Segment.Callout(
                resolved = CalloutHeader.resolveCallout(header.rawType),
                title = header.title.ifEmpty { CalloutHeader.resolveCallout(header.rawType).label },
                body = bodyLines.joinToString("\n").trim()
            )
            i = j
        }

        flushPending()
        return segments
    }
}

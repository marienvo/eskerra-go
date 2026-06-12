package com.eskerra.go.core.markdown

import com.eskerra.go.core.markdown.CalloutBlocks.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class CalloutBlocksTest {

    @Test
    fun plainBody_isSingleMarkdownSegment() {
        val segments = CalloutBlocks.segment("# Title\n\nSome text.")
        assertEquals(1, segments.size)
        assertEquals(Segment.Markdown("# Title\n\nSome text."), segments[0])
    }

    @Test
    fun calloutWithTitleAndBody_isParsed() {
        val body = "> [!tip] A tip\n> first line\n> second line"
        val segments = CalloutBlocks.segment(body)
        assertEquals(1, segments.size)
        val callout = segments[0] as Segment.Callout
        assertEquals("tip", callout.resolved.type)
        assertEquals("A tip", callout.title)
        assertEquals("first line\nsecond line", callout.body)
    }

    @Test
    fun calloutWithoutTitle_usesDefaultLabel() {
        val segments = CalloutBlocks.segment("> [!warning]\n> be careful")
        val callout = segments[0] as Segment.Callout
        assertEquals("warning", callout.resolved.type)
        assertEquals("Warning", callout.title)
        assertEquals("be careful", callout.body)
    }

    @Test
    fun markdownSurroundingCallout_isSplit() {
        val body = "intro paragraph\n> [!note] Heads up\n> body\noutro paragraph"
        val segments = CalloutBlocks.segment(body)
        assertEquals(3, segments.size)
        assertEquals(Segment.Markdown("intro paragraph"), segments[0])
        assertEquals("note", (segments[1] as Segment.Callout).resolved.type)
        assertEquals(Segment.Markdown("outro paragraph"), segments[2])
    }

    @Test
    fun plainBlockquote_isNotACallout() {
        val body = "> just a quote\n> more quote"
        val segments = CalloutBlocks.segment(body)
        assertEquals(1, segments.size)
        assert(segments[0] is Segment.Markdown)
    }

    @Test
    fun twoConsecutiveCallouts_separatedByBlankLine() {
        val body = "> [!tip] One\n> a\n\n> [!warning] Two\n> b"
        val segments = CalloutBlocks.segment(body)
        // Blank line between is a non-blockquote line → markdown segment between callouts.
        assertEquals("tip", (segments.first() as Segment.Callout).resolved.type)
        assertEquals("warning", (segments.last() as Segment.Callout).resolved.type)
    }
}

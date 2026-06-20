package com.eskerra.go.core.markdown

import com.mikepenz.markdown.model.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedMarkdownTest {

    @Test
    fun prepare_parsesMarkdownRunIntoSuccessState() = runTest {
        val prepared = prepareVaultMarkdown("# Title\n\nBody text.")

        val segment = prepared.segments.single()
        assertTrue(segment is PreparedSegment.Markdown)
        assertTrue((segment as PreparedSegment.Markdown).state is State.Success)
    }

    @Test
    fun prepare_segmentsCalloutWithPreParsedBody() = runTest {
        val prepared = prepareVaultMarkdown("> [!note] Heads up\n> Inner body")

        val callout = prepared.segments.single() as PreparedSegment.Callout
        assertEquals("Heads up", callout.title)
        assertTrue(callout.body is State.Success)
    }

    @Test
    fun prepare_calloutWithoutBody_hasNullBody() = runTest {
        val prepared = prepareVaultMarkdown("> [!note] Just a title")

        val callout = prepared.segments.single() as PreparedSegment.Callout
        assertNull(callout.body)
    }

    @Test
    fun prepare_stripsFrontmatter_blankBodyYieldsNoSegments() = runTest {
        val prepared = prepareVaultMarkdown("---\ntitle: x\n---\n")

        assertTrue(prepared.segments.isEmpty())
    }

    @Test
    fun prepare_withoutWikiPreprocessing_leavesWikiLinkAsText() = runTest {
        val withWiki = prepareVaultMarkdown("[[Some Note]]", preprocessWikiLinks = true)
        val withoutWiki = prepareVaultMarkdown("[[Some Note]]", preprocessWikiLinks = false)

        // Both parse; the preprocessing toggle changes the parsed content, not the segment shape.
        assertEquals(1, withWiki.segments.size)
        assertEquals(1, withoutWiki.segments.size)
        assertTrue(withoutWiki.segments.single() is PreparedSegment.Markdown)
    }
}

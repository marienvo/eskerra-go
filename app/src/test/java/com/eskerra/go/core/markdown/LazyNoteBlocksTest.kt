package com.eskerra.go.core.markdown

import com.mikepenz.markdown.model.State
import kotlinx.coroutines.test.runTest
import org.intellij.markdown.MarkdownElementTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyNoteBlocksTest {

    private fun markdownStates(segments: List<PreparedSegment>): List<State.Success> =
        segments.map { (it as PreparedSegment.Markdown).state as State.Success }

    @Test
    fun eachTopLevelBlock_becomesItsOwnSegment() = runTest {
        val prepared = prepareVaultMarkdown(
            """
            # Heading

            First paragraph.

            Second paragraph.
            """.trimIndent()
        )

        val segments = LazyNoteBlocks.split(prepared)

        assertEquals(3, segments.size)
        val states = markdownStates(segments)
        assertEquals(MarkdownElementTypes.ATX_1, states[0].node.children.single().type)
        assertEquals(MarkdownElementTypes.PARAGRAPH, states[1].node.children.single().type)
        assertEquals(MarkdownElementTypes.PARAGRAPH, states[2].node.children.single().type)
    }

    @Test
    fun smallUnorderedList_staysAsOneSegment() = runTest {
        val prepared = prepareVaultMarkdown("- one\n- two\n- three")

        val segments = LazyNoteBlocks.split(prepared)

        assertEquals(1, segments.size)
        val list = markdownStates(segments)[0].node.children.single()
        assertEquals(MarkdownElementTypes.UNORDERED_LIST, list.type)
        assertEquals(3, list.children.count { it.type == MarkdownElementTypes.LIST_ITEM })
    }

    @Test
    fun largeUnorderedList_chunksIntoMultipleSegments() = runTest {
        val markdown = (1..25).joinToString("\n") { "- item $it" }
        val prepared = prepareVaultMarkdown(markdown)

        val segments = LazyNoteBlocks.split(prepared)

        // ceil(25 / 10) = 3 chunks
        assertEquals(3, segments.size)
        val chunkSizes = markdownStates(segments).map { state ->
            val list = state.node.children.single()
            assertEquals(MarkdownElementTypes.UNORDERED_LIST, list.type)
            list.children.count { it.type == MarkdownElementTypes.LIST_ITEM }
        }
        assertEquals(listOf(10, 10, 5), chunkSizes)
    }

    @Test
    fun largeUnorderedList_preservesItemTextAndOrderAcrossChunks() = runTest {
        val markdown = (1..12).joinToString("\n") { "- item $it" }
        val prepared = prepareVaultMarkdown(markdown)

        val segments = LazyNoteBlocks.split(prepared)

        val itemTexts = markdownStates(segments).flatMap { state ->
            val list = state.node.children.single()
            list.children
                .filter { it.type == MarkdownElementTypes.LIST_ITEM }
                .map { state.content.substring(it.startOffset, it.endOffset).trim() }
        }
        assertEquals((1..12).map { "- item $it" }, itemTexts)
    }

    @Test
    fun sourceRanges_followTheOriginalMarkdownAcrossUnevenBlocks() = runTest {
        val longFirst = "a".repeat(100)
        val markdown = "$longFirst\n\nshort"
        val prepared = prepareVaultMarkdown(markdown)

        val blocks = LazyNoteBlocks.splitWithSourceRanges(prepared)

        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].sourceStartOffset)
        assertEquals(longFirst.length, blocks[0].sourceEndOffset)
        assertEquals(markdown.indexOf("short"), blocks[1].sourceStartOffset)
        assertEquals(markdown.length, blocks[1].sourceEndOffset)
    }

    @Test
    fun largeOrderedList_isNotChunked() = runTest {
        val markdown = (1..25).joinToString("\n") { "$it. item $it" }
        val prepared = prepareVaultMarkdown(markdown)

        val segments = LazyNoteBlocks.split(prepared)

        assertEquals(1, segments.size)
        val list = markdownStates(segments)[0].node.children.single()
        assertEquals(MarkdownElementTypes.ORDERED_LIST, list.type)
        assertEquals(25, list.children.count { it.type == MarkdownElementTypes.LIST_ITEM })
    }

    @Test
    fun calloutSegments_passThroughUnaffected() = runTest {
        val prepared = prepareVaultMarkdown("> [!note] Heads up\n> Inner body")

        val segments = LazyNoteBlocks.split(prepared)

        assertEquals(1, segments.size)
        val callout = segments.single() as PreparedSegment.Callout
        assertEquals("Heads up", callout.title)
        assertTrue(callout.body is State.Success)
    }

    @Test
    fun blankLinesAndLinkDefinitions_produceNoStraySegments() = runTest {
        val prepared = prepareVaultMarkdown(
            """
            Paragraph one.

            [ref]: http://example.com "Title"

            Paragraph two.
            """.trimIndent()
        )

        val segments = LazyNoteBlocks.split(prepared)

        // Exactly the two paragraphs -- no segment for the blank lines or the reference definition.
        assertEquals(2, segments.size)
    }

    @Test
    fun referenceStyleLink_stillResolvableAfterSplit() = runTest {
        val prepared = prepareVaultMarkdown(
            """
            [ref]: http://example.com "Title"

            See [ref link][ref].
            """.trimIndent()
        )

        val segments = LazyNoteBlocks.split(prepared)

        assertEquals(1, segments.size)
        val state = markdownStates(segments)[0]
        // The reference handler was populated by the one full parse of the whole segment, before
        // splitting -- reused as-is, not recomputed per block.
        assertTrue(state.linksLookedUp)
    }

    @Test
    fun emptyBody_producesNoSegments() = runTest {
        val prepared = prepareVaultMarkdown("")

        assertEquals(emptyList<PreparedSegment>(), LazyNoteBlocks.split(prepared))
    }
}

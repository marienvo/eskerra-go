package com.eskerra.go.core.wikilink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkParserTest {

    @Test
    fun parse_singleTitleLink_returnsTargetAndDefaultLabel() {
        val links = WikiLinkParser.parse("See [[Note Title]].")

        assertEquals(1, links.size)
        assertEquals("Note Title", links.single().target)
        assertEquals("Note Title", links.single().displayText)
        assertTrue(links.single().hasValidTarget)
    }

    @Test
    fun parse_folderPathLink_returnsPathTarget() {
        val links = WikiLinkParser.parse("See [[folder/note-title]].")

        assertEquals(1, links.size)
        assertEquals("folder/note-title", links.single().target)
    }

    @Test
    fun parse_labeledTitleLink_returnsLabel() {
        val links = WikiLinkParser.parse("See [[Note Title|Label]].")

        assertEquals(1, links.size)
        assertEquals("Note Title", links.single().target)
        assertEquals("Label", links.single().displayText)
    }

    @Test
    fun parse_labeledFolderPathLink_returnsLabel() {
        val links = WikiLinkParser.parse("See [[folder/note-title|Label]].")

        assertEquals(1, links.size)
        assertEquals("folder/note-title", links.single().target)
        assertEquals("Label", links.single().displayText)
    }

    @Test
    fun parse_trimsTargetAndLabel() {
        val links = WikiLinkParser.parse("[[  Note Title  |  Label  ]]")

        assertEquals(1, links.size)
        assertEquals("Note Title", links.single().target)
        assertEquals("Label", links.single().displayText)
    }

    @Test
    fun parse_emptyLabel_fallsBackToTarget() {
        val links = WikiLinkParser.parse("[[Note Title|   ]]")

        assertEquals(1, links.size)
        assertEquals("Note Title", links.single().displayText)
    }

    @Test
    fun parse_emptyTarget_marksInvalid() {
        val links = WikiLinkParser.parse("[[   |Label]]")

        assertEquals(1, links.size)
        assertFalse(links.single().hasValidTarget)
    }

    @Test
    fun parse_multipleLinks_returnsInSourceOrder() {
        val links = WikiLinkParser.parse("[[First]] then [[Second|Two]]")

        assertEquals(2, links.size)
        assertEquals("First", links[0].target)
        assertEquals("Second", links[1].target)
        assertEquals("Two", links[1].displayText)
    }

    @Test
    fun parse_malformedSingleBracketText_ignoresIt() {
        val links = WikiLinkParser.parse("[Note] [[Note] [Note]]")

        assertTrue(links.isEmpty())
    }

    @Test
    fun parse_nestedWikiLinkText_ignoresMalformedToken() {
        val links = WikiLinkParser.parse("[[Outer [[Inner]]]]")

        assertTrue(links.isEmpty())
    }

    @Test
    fun parse_setsSourceRangeToFullToken() {
        val markdown = "See [[Note Title]]."
        val links = WikiLinkParser.parse(markdown)

        assertEquals(4..17, links.single().sourceRange)
        assertEquals("[[Note Title]]", markdown.substring(links.single().sourceRange))
    }

    @Test
    fun parse_labeledLink_preservesAdditionalPipeCharactersInLabel() {
        val links = WikiLinkParser.parse("[[Target|Label|Extra]]")

        assertEquals(1, links.size)
        assertEquals("Target", links.single().target)
        assertEquals("Label|Extra", links.single().displayText)
    }
}

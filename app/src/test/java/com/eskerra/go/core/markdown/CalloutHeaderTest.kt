package com.eskerra.go.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Vectors ported from `callouts.test.ts`. */
class CalloutHeaderTest {

    // --- resolveCallout ---

    @Test
    fun resolve_canonicalTypesCaseInsensitively() {
        assertEquals("tip", CalloutHeader.resolveCallout("TIP").type)
        assertEquals("Tip", CalloutHeader.resolveCallout("Tip").label)
        assertEquals("warning", CalloutHeader.resolveCallout("WARNING").type)
    }

    @Test
    fun resolve_aliasesToCanonicalKeys() {
        assertEquals("tip", CalloutHeader.resolveCallout("hint").type)
        assertEquals("abstract", CalloutHeader.resolveCallout("tldr").type)
        assertEquals("quote", CalloutHeader.resolveCallout("cite").type)
        assertEquals("danger", CalloutHeader.resolveCallout("error").type)
    }

    @Test
    fun resolve_unknownFallsBackToNote() {
        val r = CalloutHeader.resolveCallout("unknown-xyz")
        assertEquals("note", r.type)
        assertEquals("edit", r.icon)
    }

    // --- matchCalloutHeader ---

    @Test
    fun match_simpleCalloutWithCustomTitle() {
        val m = CalloutHeader.matchCalloutHeader("> [!tip] A tip")
        assertNotNull(m)
        assertEquals("tip", m!!.type)
        assertEquals("tip", m.rawType)
        assertEquals("A tip", m.title)
        assertEquals(2, m.startCol)
        assertEquals(2 + "[!tip]".length, m.endCol)
    }

    @Test
    fun match_uppercaseType() {
        val m = CalloutHeader.matchCalloutHeader("> [!INFO]")
        assertEquals("info", m!!.type)
        assertEquals("", m.title)
    }

    @Test
    fun match_includesFoldMarkerInColumnSpan() {
        val line = "> [!warning]+ Some warning"
        val m = CalloutHeader.matchCalloutHeader(line)
        assertEquals("warning", m!!.type)
        assertEquals("Some warning", m.title)
        assertEquals("[!warning]+", line.substring(m.startCol, m.endCol))
    }

    @Test
    fun match_resolvesAliasInBracket() {
        val m = CalloutHeader.matchCalloutHeader("> [!hint] body")
        assertEquals("tip", m!!.type)
        assertEquals("body", m.title)
    }

    @Test
    fun match_rejectsNestedQuoteMarkers() {
        assertNull(CalloutHeader.matchCalloutHeader("> > [!tip] nested"))
        assertNull(CalloutHeader.matchCalloutHeader(">> [!tip] x"))
    }

    @Test
    fun match_allowsLeadingWhitespaceBeforeQuote() {
        val m = CalloutHeader.matchCalloutHeader("  > [!note] ok")
        assertNotNull(m)
        assertEquals("note", m!!.type)
    }

    @Test
    fun match_unknownBracketTypeResolvesToNote() {
        val m = CalloutHeader.matchCalloutHeader("> [!unknown-xyz] Title")
        assertNotNull(m)
        assertEquals("note", m!!.type)
        assertEquals("Title", m.title)
    }

    @Test
    fun match_plainBlockquoteIsNotCallout() {
        assertNull(CalloutHeader.matchCalloutHeader("> just a quote"))
    }
}

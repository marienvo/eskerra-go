package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlTitleExtractorTest {

    @Test
    fun extractsPlainTitle() {
        assertEquals("Hello", HtmlTitleExtractor.extract("<html><head><title>Hello</title>"))
    }

    @Test
    fun isCaseInsensitiveAndToleratesAttributes() {
        assertEquals("Hello", HtmlTitleExtractor.extract("""<TITLE data-x="1">Hello</TITLE>"""))
    }

    @Test
    fun collapsesMultiLineTitle() {
        assertEquals("A long title", HtmlTitleExtractor.extract("<title>A long\n   title</title>"))
    }

    @Test
    fun stripsNestedTags() {
        assertEquals("Hello world", HtmlTitleExtractor.extract("<title>Hello <b>world</b></title>"))
    }

    @Test
    fun decodesNamedEntities() {
        assertEquals(
            "Tom & Jerry <live>",
            HtmlTitleExtractor.extract("<title>Tom &amp; Jerry &lt;live&gt;</title>")
        )
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("café — 1", HtmlTitleExtractor.extract("<title>caf&#233; &#x2014; 1</title>"))
    }

    @Test
    fun leavesUnknownEntityVerbatim() {
        assertEquals("a &bogus; b", HtmlTitleExtractor.extract("<title>a &bogus; b</title>"))
    }

    @Test
    fun returnsFirstTitleOnly() {
        assertEquals("One", HtmlTitleExtractor.extract("<title>One</title><title>Two</title>"))
    }

    @Test
    fun returnsNullWhenTitleIsEmpty() {
        assertNull(HtmlTitleExtractor.extract("<title></title>"))
        assertNull(HtmlTitleExtractor.extract("<title>   </title>"))
    }

    @Test
    fun returnsNullWhenThereIsNoTitle() {
        assertNull(HtmlTitleExtractor.extract("<html><head></head><body>hi</body></html>"))
    }

    @Test
    fun returnsNullWhenTitleIsCutOffMidTag() {
        assertNull(HtmlTitleExtractor.extract("<html><head><title>A truncated docu"))
    }
}

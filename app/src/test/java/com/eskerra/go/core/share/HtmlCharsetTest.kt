package com.eskerra.go.core.share

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlCharsetTest {

    @Test
    fun headerCharsetWins() {
        val charset = HtmlCharset.resolve("ISO-8859-1", """<meta charset="utf-8">""")

        assertEquals(StandardCharsets.ISO_8859_1, charset)
    }

    @Test
    fun fallsBackToMetaCharset() {
        val charset = HtmlCharset.resolve(null, """<meta charset="iso-8859-1">""")

        assertEquals(StandardCharsets.ISO_8859_1, charset)
    }

    @Test
    fun readsHttpEquivForm() {
        val head = """<meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">"""

        assertEquals(StandardCharsets.ISO_8859_1, HtmlCharset.resolve(null, head))
    }

    @Test
    fun unknownNameFallsBackToUtf8() {
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.resolve("not-a-charset", ""))
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.resolve(null, "<meta charset=nonsense>"))
    }

    @Test
    fun nothingDeclaredFallsBackToUtf8() {
        assertEquals(StandardCharsets.UTF_8, HtmlCharset.resolve(null, "<html><head></head>"))
    }
}

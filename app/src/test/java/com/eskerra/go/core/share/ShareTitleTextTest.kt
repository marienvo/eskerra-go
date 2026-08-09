package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTitleTextTest {

    @Test
    fun sanitizeTitleLine_collapsesNewlinesAndTabs() {
        assertEquals(
            "Kotlin coroutines guide",
            ShareTitleText.sanitizeTitleLine("Kotlin\n coroutines\tguide")
        )
    }

    @Test
    fun sanitizeTitleLine_stripsH1Prefix() {
        assertEquals("A title", ShareTitleText.sanitizeTitleLine("# A title"))
    }

    @Test
    fun sanitizeTitleLine_returnsNullForBlank() {
        assertNull(ShareTitleText.sanitizeTitleLine("   \n\t "))
        assertNull(ShareTitleText.sanitizeTitleLine(null))
    }

    @Test
    fun sanitizeTitleLine_rejectsTitleThatIsItselfAUrl() {
        assertNull(ShareTitleText.sanitizeTitleLine("https://example.com/article"))
    }

    @Test
    fun sanitizeTitleLine_truncatesOnWhitespaceBoundary() {
        val long = List(40) { "word" }.joinToString(" ")
        val title = requireNonNull(ShareTitleText.sanitizeTitleLine(long))
        assertTrue(title.length <= ShareTitleText.MAX_TITLE_LENGTH)
        assertTrue(title.endsWith("word"))
    }

    @Test
    fun sanitizeTitleLine_truncatesUnbrokenRunHard() {
        val long = "a".repeat(300)
        val title = requireNonNull(ShareTitleText.sanitizeTitleLine(long))
        assertEquals(ShareTitleText.MAX_TITLE_LENGTH, title.length)
    }

    private fun requireNonNull(value: String?): String = value ?: error("expected a title")
}

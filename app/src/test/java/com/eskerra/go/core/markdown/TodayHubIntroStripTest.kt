package com.eskerra.go.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayHubIntroStripTest {

    @Test
    fun stripsMatchingTitleBeforeBody() {
        val markdown = """
            # Daily hub

            Intro body here.
        """.trimIndent()

        assertEquals(
            "Intro body here.",
            TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        )
    }

    @Test
    fun leavesMarkdownUnchangedWhenTitleDoesNotMatch() {
        val markdown = """
            # Other heading

            Intro body here.
        """.trimIndent()

        assertEquals(
            markdown,
            TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        )
    }

    @Test
    fun leavesMarkdownUnchangedWhenTitleIsBlank() {
        val markdown = "# Daily hub\n\nBody"
        assertEquals(markdown, TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, null))
        assertEquals(markdown, TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "   "))
    }

    @Test
    fun ignoresHeadingInsideBacktickFence() {
        val markdown = """
            ```
            # Example
            ```

            Body text.
        """.trimIndent()

        val stripped = TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        assertTrue(stripped.contains("# Example"))
        assertTrue(stripped.contains("Body text."))
    }

    @Test
    fun ignoresHeadingInsideTildeFence() {
        val markdown = """
            ~~~
            # Example
            ~~~

            Body text.
        """.trimIndent()

        val stripped = TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        assertTrue(stripped.contains("# Example"))
    }

    @Test
    fun ignoresHeadingInsideMixedMarkerFence() {
        val markdown = """
            ```
            ~~~
            # Example
            ```

            Body text.
        """.trimIndent()

        val stripped = TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        assertTrue(stripped.contains("~~~"))
        assertTrue(stripped.contains("# Example"))
    }

    @Test
    fun ignoresHeadingInsideLongerOpeningFence() {
        val markdown = """
            ````
            ```
            # Example
            ````

            Body text.
        """.trimIndent()

        val stripped = TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Daily hub")
        assertTrue(stripped.contains("# Example"))
        assertTrue(stripped.contains("```"))
    }

    @Test
    fun doesNotStripArbitraryH1WhenTitleIsExample() {
        val markdown = """
            # Daily hub

            Body text.
        """.trimIndent()

        assertEquals(
            markdown,
            TodayHubIntroStrip.stripLeadingAtxH1ForTitle(markdown, "Example")
        )
    }

    @Test
    fun isAtxH1ForTitle_stripsClosingHashes() {
        assertTrue(TodayHubIntroStrip.isAtxH1ForTitle("# Daily hub #", "Daily hub"))
        assertTrue(TodayHubIntroStrip.isAtxH1ForTitle("# Daily hub ##", "Daily hub"))
    }
}

package com.eskerra.go.core.share

import com.eskerra.go.core.inbox.InboxMarkdownFileName
import com.eskerra.go.core.model.InboxNoteDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Line 1 of a draft becomes the note's h1 **and its filename**. These invariants hold for every
 * share shape, and they are the reason the URL bar in [SharedUrl] is set so high.
 */
class ShareDraftInvariantsTest {

    private val shares = listOf(
        SharedContent("https://example.com/a"),
        SharedContent("https://example.com/a", "A page title"),
        SharedContent("https://example.com/a", "https://example.com/a"),
        SharedContent("look at this https://example.com/a"),
        SharedContent("Some prose\nover two lines"),
        SharedContent("Some prose", "# A subject with h1 prefix"),
        SharedContent("   ", "Subject only"),
        SharedContent("x".repeat(50_000)),
        SharedContent("https://example.com/a", List(60) { "word" }.joinToString(" ")),
        SharedContent("line one\r\nline two", "Windows newlines")
    )

    @Test
    fun titleLineIsNeverAUrl() {
        forEachDraft { shared, draft ->
            val titleLine = draft.text.lineSequence().first()
            assertFalse("title line was a URL for $shared", SharedUrl.looksLikeUrl(titleLine))
            assertFalse(
                "extracted title was a URL for $shared",
                SharedUrl.looksLikeUrl(InboxNoteDraft.extractTitleLine(draft.text))
            )
        }
    }

    @Test
    fun caretAlwaysSitsOnLineOne() {
        forEachDraft { shared, draft ->
            assertTrue("caret out of bounds for $shared", draft.caretOffset in 0..draft.text.length)
            assertFalse(
                "caret was past line 1 for $shared",
                draft.text.take(draft.caretOffset).contains('\n')
            )
        }
    }

    @Test
    fun filenameStemStaysBounded() {
        forEachDraft { shared, draft ->
            val title = InboxNoteDraft.extractTitleLine(draft.text)
            val stem = InboxMarkdownFileName.sanitizeFileName(title, nowEpochMillis = 0L)
            assertTrue(
                "filename stem too long for $shared: ${stem.length}",
                stem.length <= ShareTitleText.MAX_TITLE_LENGTH
            )
        }
    }

    @Test
    fun fetchIsOnlyRequestedWhenTheTitleLineIsStillEmpty() {
        forEachDraft { shared, draft ->
            if (draft.titleFetchUrl != null) {
                assertTrue(
                    "fetch requested for a draft that already has a title: $shared",
                    draft.text.lineSequence().first().isEmpty()
                )
                assertTrue(SharedUrl.looksLikeUrl(draft.titleFetchUrl!!))
            }
        }
    }

    private fun forEachDraft(assertion: (SharedContent, ShareDraft) -> Unit) {
        shares.forEach { shared ->
            val draft = BuildShareDraft.build(shared) ?: error("expected a draft for $shared")
            assertion(shared, draft)
        }
    }
}

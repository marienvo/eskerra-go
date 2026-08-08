package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildShareDraftTest {

    @Test
    fun subjectWinsForUrlShare_andNeedsNoFetch() {
        val draft = build(
            SharedContent(text = "https://example.com/a", subject = "Kotlin coroutines guide")
        )

        assertEquals("Kotlin coroutines guide\n\nhttps://example.com/a", draft.text)
        assertEquals("Kotlin coroutines guide".length, draft.caretOffset)
        assertNull(draft.titleFetchUrl)
    }

    @Test
    fun subjectWinsForPlainTextShare() {
        val draft = build(SharedContent(text = "Some shared paragraph", subject = "A subject"))

        assertEquals("A subject\n\nSome shared paragraph", draft.text)
        assertEquals("A subject".length, draft.caretOffset)
        assertNull(draft.titleFetchUrl)
    }

    @Test
    fun urlWithoutSubject_leavesTitleLineEmptyAndRequestsFetch() {
        val draft = build(SharedContent(text = "https://example.com/a"))

        assertEquals("\n\nhttps://example.com/a", draft.text)
        assertEquals(0, draft.caretOffset)
        assertEquals("https://example.com/a", draft.titleFetchUrl)
    }

    @Test
    fun plainTextWithoutSubject_leavesTitleLineEmptyAndRequestsNoFetch() {
        val draft = build(SharedContent(text = "Just some thought"))

        assertEquals("\n\nJust some thought", draft.text)
        assertEquals(0, draft.caretOffset)
        assertNull(draft.titleFetchUrl)
    }

    @Test
    fun subjectOnlyShare_becomesTitleOnly() {
        val draft = build(SharedContent(text = "   ", subject = "Just a title"))

        assertEquals("Just a title", draft.text)
        assertEquals("Just a title".length, draft.caretOffset)
    }

    @Test
    fun subjectEqualToBody_isDropped() {
        val draft = build(SharedContent(text = "Same text", subject = "Same text"))

        assertEquals("\n\nSame text", draft.text)
        assertEquals(0, draft.caretOffset)
    }

    @Test
    fun subjectThatIsTheUrlAgain_isDropped() {
        val draft = build(
            SharedContent(text = "https://example.com/a", subject = "https://example.com/a")
        )

        assertEquals("\n\nhttps://example.com/a", draft.text)
        assertEquals("https://example.com/a", draft.titleFetchUrl)
    }

    @Test
    fun blankShare_returnsNull() {
        assertNull(BuildShareDraft.build(SharedContent(text = "   ", subject = "  ")))
        assertNull(BuildShareDraft.build(SharedContent(text = "")))
    }

    @Test
    fun crlfTextIsNormalized() {
        val draft = build(SharedContent(text = "line one\r\nline two"))

        assertEquals("\n\nline one\nline two", draft.text)
    }

    @Test
    fun overlongBodyIsCapped() {
        val draft = build(SharedContent(text = "x".repeat(500_000)))

        assertTrue(draft.text.length <= BuildShareDraft.MAX_SHARED_BODY_CHARS + 3)
        assertTrue(draft.text.endsWith("…"))
        assertEquals(0, draft.caretOffset)
    }

    @Test
    fun overlongSubjectIsTruncatedIntoTheTitleLine() {
        val subject = List(60) { "word" }.joinToString(" ")
        val draft = build(SharedContent(text = "https://example.com/a", subject = subject))

        val titleLine = draft.text.lineSequence().first()
        assertTrue(titleLine.length <= ShareTitleText.MAX_TITLE_LENGTH)
        assertEquals(titleLine.length, draft.caretOffset)
    }

    private fun build(shared: SharedContent): ShareDraft =
        BuildShareDraft.build(shared) ?: error("expected a draft for $shared")
}

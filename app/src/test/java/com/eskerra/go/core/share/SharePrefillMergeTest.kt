package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePrefillMergeTest {

    private val prefill = SharePrefill(
        token = 1L,
        stage = SharePrefill.Stage.Immediate,
        text = "\n\nhttps://example.com/a",
        caretOffset = 0
    )

    @Test
    fun blankDraftIsReplaced() {
        val merged = SharePrefillMerge.mergeImmediate("", prefill)

        assertEquals(prefill.text, merged.text)
        assertEquals(0, merged.caretOffset)
        assertTrue(merged.replacedDraft)
    }

    @Test
    fun whitespaceOnlyDraftIsReplaced() {
        assertTrue(SharePrefillMerge.mergeImmediate("  \n ", prefill).replacedDraft)
    }

    @Test
    fun typedDraftIsPreservedAndSharedContentAppended() {
        val merged = SharePrefillMerge.mergeImmediate("My own title\n\nMy own body", prefill)

        assertEquals("My own title\n\nMy own body\n\nhttps://example.com/a", merged.text)
        assertEquals(merged.text.length, merged.caretOffset)
        assertFalse(merged.replacedDraft)
    }

    @Test
    fun titleUpgradeRaisesTitleOntoTheEmptyFirstLine() {
        val upgraded = ShareTitleUpgrade.apply(prefill.text, "A fetched page title")

        assertEquals("A fetched page title\n\nhttps://example.com/a", upgraded?.text)
        assertEquals("A fetched page title".length, upgraded?.caretOffset)
    }

    @Test
    fun titleUpgradeRejectsUnusableTitles() {
        assertNull(ShareTitleUpgrade.apply(prefill.text, "   "))
        assertNull(ShareTitleUpgrade.apply(prefill.text, "https://example.com/a"))
    }

    @Test
    fun titleUpgradeRejectsDraftThatAlreadyHasATitleLine() {
        assertNull(ShareTitleUpgrade.apply("Existing title\n\nbody", "A fetched title"))
    }
}

package com.eskerra.go.ui.markdown

import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteRegistry
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMarkdownAnnotatorTest {

    @Test
    fun defaultConfig_rendersSoftBreaksAsSpaces() {
        val annotator = buildAnnotator()

        assertFalse(annotator.config.eolAsNewLine)
    }

    @Test
    fun preserveLineBreaks_rendersSoftBreaksAsNewlines() {
        val annotator = buildAnnotator(preserveLineBreaks = true)

        assertTrue(annotator.config.eolAsNewLine)
    }

    private fun buildAnnotator(preserveLineBreaks: Boolean = false) = VaultMarkdownAnnotator.build(
        registry = NoteRegistry(emptyList()),
        status = VaultReadonlyLink.IndexStatus.READY,
        now = LocalDateTime.of(2026, 7, 5, 12, 0),
        onLinkTap = {},
        preserveLineBreaks = preserveLineBreaks
    )
}

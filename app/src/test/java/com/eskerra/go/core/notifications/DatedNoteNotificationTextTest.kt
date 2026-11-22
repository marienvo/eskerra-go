package com.eskerra.go.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class DatedNoteNotificationTextTest {
    private val noteId = "Flow/Work/2026-11-22.md"

    @Test
    fun usesYamlStringForFirstAndRepeatedNotifications() {
        val markdown = "---\nnotification: \"Stand up: choose a focus\"\n---\n# Work"
        assertEquals(
            "Stand up: choose a focus",
            DatedNoteNotificationText.resolve(noteId, markdown)
        )
    }

    @Test
    fun fallsBackForMissingBlankNonStringAndInvalidValues() {
        val cases = listOf(
            "# Work",
            "---\ntitle: Work\n---\n# Work",
            "---\nnotification: \"  \"\n---\n# Work",
            "---\nnotification: 42\n---\n# Work",
            "---\nnotification: [broken\n---\n# Work"
        )
        cases.forEach { markdown ->
            assertEquals("2026-11-22", DatedNoteNotificationText.resolve(noteId, markdown))
        }
    }
}

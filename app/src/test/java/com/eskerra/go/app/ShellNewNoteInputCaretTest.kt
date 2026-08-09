package com.eskerra.go.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShellNewNoteInputCaretTest {

    @Test
    fun sameTextKeepsTheFieldUntouched() {
        val current = TextFieldValue("Draft", TextRange(2))

        assertSame(current, syncFieldValue(current, "Draft"))
    }

    @Test
    fun saveResetSnapsFieldToEmpty() {
        val synced = syncFieldValue(TextFieldValue("Draft", TextRange(5)), "")

        assertEquals("", synced.text)
        assertEquals(TextRange(0), synced.selection)
    }

    @Test
    fun externalTextChangePutsCaretAtTheEnd() {
        val synced = syncFieldValue(TextFieldValue("old", TextRange(0)), "a new draft")

        assertEquals("a new draft", synced.text)
        assertEquals(TextRange("a new draft".length), synced.selection)
    }

    @Test
    fun caretFieldValuePlacesCaretAtOffset() {
        val value = caretFieldValue("Title\n\nbody", 5)

        assertEquals(TextRange(5), value.selection)
    }

    @Test
    fun caretFieldValueClampsOutOfRangeOffsets() {
        assertEquals(TextRange(0), caretFieldValue("abc", -4).selection)
        assertEquals(TextRange(3), caretFieldValue("abc", 99).selection)
    }

    @Test
    fun caretFieldValueAcceptsTheEmptyTitleLine() {
        val value = caretFieldValue("\n\nhttps://example.com", 0)

        assertEquals(TextRange(0), value.selection)
        assertEquals("\n\nhttps://example.com", value.text)
    }
}

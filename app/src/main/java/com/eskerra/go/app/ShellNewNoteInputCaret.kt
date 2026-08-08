package com.eskerra.go.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * One-shot instruction for the compose pill's text field.
 *
 * The text itself stays externally owned (the composer ViewModel's draft, or the search query);
 * only focus and caret are the field's business. Each signal carries a [token] so a recomposition
 * can never re-run it — these are events, not state.
 */
sealed interface ShellFieldSignal {

    val token: Long

    /** Focus the field, put the caret at [offset], and raise the keyboard. */
    data class PlaceCaret(override val token: Long, val offset: Int) : ShellFieldSignal

    /** Drop focus and let the keyboard go — what a completed save should always do. */
    data class ReleaseFocus(override val token: Long) : ShellFieldSignal
}

/**
 * The externally owned text changed underneath the field (a save reset it to empty, the pill
 * switched between note and search, a share prefilled it): the external text is the truth, and
 * the caret goes to its end. Returns [current] unchanged when the text already matches, so
 * ordinary typing — where the ViewModel echoes the same string back — never disturbs the field
 * or its IME composition.
 */
internal fun syncFieldValue(current: TextFieldValue, externalText: String): TextFieldValue =
    if (current.text == externalText) {
        current
    } else {
        TextFieldValue(externalText, TextRange(externalText.length))
    }

/** Field value for an explicit caret placement; [offset] is clamped into [text]. */
internal fun caretFieldValue(text: String, offset: Int): TextFieldValue =
    TextFieldValue(text, TextRange(offset.coerceIn(0, text.length)))

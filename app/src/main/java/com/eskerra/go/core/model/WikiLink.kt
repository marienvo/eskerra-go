package com.eskerra.go.core.model

/**
 * A `[[wiki link]]` parsed out of note content. [target] is the note this link
 * points to; [displayText] is what the reader shows.
 */
data class WikiLink(val target: String, val displayText: String)

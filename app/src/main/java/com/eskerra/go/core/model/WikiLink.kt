package com.eskerra.go.core.model

/**
 * A `[[wiki link]]` parsed out of note content. [target] is the note this link
 * points to; [displayText] is what the reader shows.
 */
data class WikiLink(
    val target: String,
    val displayText: String,
    val sourceRange: IntRange,
    val hasValidTarget: Boolean
) {
    /** Compatibility constructor for call sites that do not yet track source ranges. */
    constructor(target: String, displayText: String) : this(
        target = target,
        displayText = displayText,
        sourceRange = 0..0,
        hasValidTarget = target.trim().isNotEmpty()
    )
}

/** Trimmed wiki-link target text from the parser. Does not resolve notes by itself. */
@JvmInline
value class WikiLinkTarget(val value: String)

/** Trimmed wiki-link label text from the parser. Does not encode renderer behavior. */
@JvmInline
value class WikiLinkLabel(val value: String)

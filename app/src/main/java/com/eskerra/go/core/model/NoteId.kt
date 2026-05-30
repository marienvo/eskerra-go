package com.eskerra.go.core.model

/**
 * Stable identifier for a note. Wraps a plain string so that note identities are
 * type-safe and never confused with arbitrary strings such as titles.
 */
@JvmInline
value class NoteId(val value: String)

package com.eskerra.go.core.notifications

data class DatedNoteRecord(
    val noteId: String,
    val folder: String,
    val date: String,
    val firstSeenAtEpochMillis: Long,
    val message: String
)

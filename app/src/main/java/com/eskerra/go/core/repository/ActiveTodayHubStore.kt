package com.eskerra.go.core.repository

/**
 * Persists the last active Today hub note id (spec §11.1) so a cold start reopens the same hub.
 * Mirrors the reference app's `activeTodayHubStorage`.
 */
interface ActiveTodayHubStore {
    suspend fun read(): String?
    suspend fun save(noteId: String)
}

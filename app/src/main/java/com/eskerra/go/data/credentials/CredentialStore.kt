package com.eskerra.go.data.credentials

/** Stores workspace credentials separately from workspace metadata. */
interface CredentialStore {
    suspend fun saveToken(relativePath: String, token: String): Result<Unit>

    /** Returns the stored token, or null when no credential exists for [relativePath]. */
    suspend fun readToken(relativePath: String): Result<String?>

    suspend fun clear(relativePath: String): Result<Unit>
}

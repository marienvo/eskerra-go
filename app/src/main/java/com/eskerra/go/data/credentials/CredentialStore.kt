package com.eskerra.go.data.credentials

/** Stores workspace credentials separately from workspace metadata. */
interface CredentialStore {
    suspend fun saveToken(relativePath: String, token: String): Result<Unit>
    suspend fun clear(relativePath: String): Result<Unit>
}

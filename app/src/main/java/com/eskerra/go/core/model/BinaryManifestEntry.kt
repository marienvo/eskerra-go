package com.eskerra.go.core.model

import kotlinx.serialization.Serializable

/**
 * Record of one binary this device downloaded from R2. Persisted outside the vault
 * working tree so the sync can delete only files it placed there (never unrelated
 * gitignored files).
 *
 * [relPath] is vault-root-relative (the R2 key with its `binaries/` prefix stripped).
 */
@Serializable
data class BinaryManifestEntry(
    val relPath: String,
    val key: String,
    val size: Long,
    val etag: String
)

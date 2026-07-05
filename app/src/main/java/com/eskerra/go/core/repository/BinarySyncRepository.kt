package com.eskerra.go.core.repository

import com.eskerra.go.core.model.BinaryManifestEntry
import com.eskerra.go.core.model.R2BinaryObject
import com.eskerra.go.core.model.R2Config
import java.io.File

/**
 * Transport + storage seam for the R2 → device binaries sync. Orchestration lives
 * in [com.eskerra.go.core.usecase.SyncBinaries]; this interface hides the R2 HTTP
 * client, the vault `.gitignore` evaluation, local file placement, and the
 * download manifest so the use case stays JVM-unit-testable.
 *
 * All [relPath] values are vault-root-relative (R2 key minus its `binaries/` prefix).
 */
interface BinarySyncRepository {

    /** Lists every object under the R2 `binaries/` prefix. Throws on transport/HTTP errors. */
    suspend fun listRemoteBinaries(config: R2Config): List<R2BinaryObject>

    /** Retains only the [relPaths] the vault `.gitignore` would ignore (the download gate). */
    suspend fun retainIgnored(workspaceRoot: File, relPaths: List<String>): List<String>

    /** Downloads object [key] to `<workspaceRoot>/<relPath>`, creating parent dirs. */
    suspend fun downloadBinary(config: R2Config, key: String, workspaceRoot: File, relPath: String)

    /** Deletes `<workspaceRoot>/<relPath>` when present. */
    suspend fun deleteLocalBinary(workspaceRoot: File, relPath: String)

    /** Reads the persisted download manifest (empty when absent or unreadable). */
    suspend fun readManifest(): List<BinaryManifestEntry>

    /** Overwrites the persisted download manifest. */
    suspend fun writeManifest(entries: List<BinaryManifestEntry>)

    /** On-disk size of `<workspaceRoot>/<relPath>`, or null when the file is missing. */
    fun localSize(workspaceRoot: File, relPath: String): Long?
}

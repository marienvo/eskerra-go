package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.BinaryManifestEntry
import com.eskerra.go.core.model.BinarySyncSummary
import com.eskerra.go.core.repository.BinarySyncRepository
import com.eskerra.go.core.vault.R2Settings
import com.eskerra.go.core.vault.VaultLayout
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Syncs the R2 `binaries/` prefix down to the vault working tree. R2 is leading:
 *
 * 1. Only objects whose (prefix-stripped) target path the vault `.gitignore` would
 *    ignore are downloaded — a safety gate so tracked files are never clobbered.
 * 2. Objects that disappear from R2 are removed locally (tracked via the manifest, so
 *    only files this sync placed are ever deleted).
 *
 * The `binaries/` prefix maps onto the vault root: `binaries/<path>` → `<root>/<path>`.
 */
class SyncBinaries(
    private val repository: BinarySyncRepository,
    private val loadVaultSettings: LoadVaultSettings
) {

    suspend operator fun invoke(workspaceRoot: File): BinarySyncSummary {
        val settings = loadVaultSettings(workspaceRoot).getOrElse {
            return BinarySyncSummary.Failed(it.message ?: "Failed to read vault settings.")
        }
        if (!R2Settings.isVaultR2PlaylistConfigured(settings)) {
            return BinarySyncSummary.NotConfigured
        }
        val config = settings.r2 ?: return BinarySyncSummary.NotConfigured

        var manifestDirty = false
        val manifest = repository.readManifest().associateBy { it.relPath }.toMutableMap()
        return try {
            val remote = repository.listRemoteBinaries(config)
            val candidates = remote.mapNotNull { obj ->
                val relPath = obj.key.removePrefix(VaultLayout.BINARIES_PREFIX)
                if (relPath.isBlank() || relPath == obj.key) return@mapNotNull null
                if (WorkspacePaths.validateRelativePath(relPath).isFailure) return@mapNotNull null
                relPath to obj
            }

            val ignored = repository.retainIgnored(
                workspaceRoot,
                candidates.map {
                    it.first
                }
            ).toSet()
            val kept = candidates.filter { it.first in ignored }
            val skipped = candidates.size - kept.size

            var downloaded = 0
            for ((relPath, obj) in kept) {
                val known = manifest[relPath]
                val onDisk = repository.localSize(workspaceRoot, relPath)
                val upToDate = known != null &&
                    onDisk == obj.size &&
                    known.size == obj.size &&
                    known.etag == obj.etag
                if (!upToDate) {
                    repository.downloadBinary(config, obj.key, workspaceRoot, relPath)
                    manifest[relPath] =
                        BinaryManifestEntry(relPath, obj.key, obj.size, obj.etag)
                    manifestDirty = true
                    downloaded++
                }
            }

            val remoteKeys = remote.mapTo(HashSet()) { it.key }
            var deleted = 0
            for (entry in manifest.values.toList()) {
                if (entry.key !in remoteKeys) {
                    repository.deleteLocalBinary(workspaceRoot, entry.relPath)
                    manifest.remove(entry.relPath)
                    manifestDirty = true
                    deleted++
                }
            }

            BinarySyncSummary.Completed(
                downloaded = downloaded,
                deleted = deleted,
                skipped = skipped
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            BinarySyncSummary.Failed(error.message ?: "Binaries sync failed.")
        } finally {
            if (manifestDirty) {
                repository.writeManifest(manifest.values.sortedBy { it.relPath })
            }
        }
    }
}

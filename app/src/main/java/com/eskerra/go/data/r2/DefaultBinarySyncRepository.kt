package com.eskerra.go.data.r2

import com.eskerra.go.core.model.BinaryManifestEntry
import com.eskerra.go.core.model.R2BinaryObject
import com.eskerra.go.core.model.R2Config
import com.eskerra.go.core.repository.BinarySyncRepository
import com.eskerra.go.data.git.GitignoreMatcher
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BinarySyncRepository] backed by [R2BinaryObjectClient] (R2 transport), the vault
 * [GitignoreMatcher] (download gate), and a JSON [BinaryManifestStore]. Blocking HTTP
 * and file I/O run on [ioDispatcher]. All local paths resolve through [WorkspacePaths]
 * so they can never escape the workspace root.
 */
class DefaultBinarySyncRepository(
    private val objectClient: R2BinaryObjectClient,
    private val manifestStore: BinaryManifestStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val matcherFactory: (File) -> GitignoreMatcher = GitignoreMatcher::forWorkspace
) : BinarySyncRepository {

    override suspend fun listRemoteBinaries(config: R2Config): List<R2BinaryObject> =
        withContext(ioDispatcher) { objectClient.list(config) }

    override suspend fun retainIgnored(workspaceRoot: File, relPaths: List<String>): List<String> =
        withContext(ioDispatcher) {
            val matcher = matcherFactory(workspaceRoot)
            relPaths.filter { matcher.isIgnored(it) }
        }

    override suspend fun downloadBinary(
        config: R2Config,
        key: String,
        workspaceRoot: File,
        relPath: String
    ) = withContext(ioDispatcher) {
        val dest = WorkspacePaths.resolve(workspaceRoot, relPath).getOrThrow()
        objectClient.download(config, key, dest)
    }

    override suspend fun deleteLocalBinary(workspaceRoot: File, relPath: String) {
        withContext(ioDispatcher) {
            WorkspacePaths.resolve(workspaceRoot, relPath).getOrNull()
                ?.takeIf { it.isFile }
                ?.delete()
        }
    }

    override suspend fun readManifest(): List<BinaryManifestEntry> =
        withContext(ioDispatcher) { manifestStore.read() }

    override suspend fun writeManifest(entries: List<BinaryManifestEntry>) {
        withContext(ioDispatcher) { manifestStore.write(entries) }
    }

    override fun localSize(workspaceRoot: File, relPath: String): Long? =
        WorkspacePaths.resolve(workspaceRoot, relPath).getOrNull()
            ?.takeIf { it.isFile }
            ?.length()
}

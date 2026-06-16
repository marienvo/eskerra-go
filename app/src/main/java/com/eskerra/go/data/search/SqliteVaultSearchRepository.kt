package com.eskerra.go.data.search

import android.content.Context
import android.database.sqlite.SQLiteException
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.repository.SearchOutcome
import com.eskerra.go.core.repository.VaultSearchRepository
import com.eskerra.go.core.search.Fts5Query
import com.eskerra.go.core.search.SearchCandidate
import com.eskerra.go.core.search.SearchRanker
import com.eskerra.go.core.search.VaultSearchException
import com.eskerra.go.core.search.VaultSearchIndexStatus
import com.eskerra.go.core.search.compareVaultSearchNotes
import com.eskerra.go.core.search.rankedNoteToResult
import com.eskerra.go.data.workspace.WorkspacePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SqliteVaultSearchRepository(private val appContext: Context) : VaultSearchRepository {

    private val mutex = Mutex()
    private var openHelper: VaultSearchDatabase? = null
    private var openWorkspaceKey: String? = null

    override suspend fun status(config: WorkspaceConfig, filesDir: File): VaultSearchIndexStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val db = openDatabase(config, filesDir) ?: return@withContext emptyStatus()
                    VaultSearchIndexer.readStatus(db.ensureOpen())
                }
            }.getOrElse { emptyStatus() }
        }

    override suspend fun maintain(config: WorkspaceConfig, filesDir: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    runMaintainLocked(config, filesDir)
                }
            }.recoverCatching { error ->
                val mapped = VaultSearchSqliteErrorMapper.map(error)
                if (mapped.error.canRetry()) {
                    mutex.withLock {
                        repairLocked(config, filesDir)
                        runMaintainLocked(config, filesDir)
                    }
                } else {
                    throw mapped
                }
            }
        }

    override suspend fun touchPaths(
        config: WorkspaceConfig,
        filesDir: File,
        paths: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (paths.isEmpty()) return@runCatching
            mutex.withLock {
                val workspace = resolveWorkspace(config, filesDir).getOrThrow()
                val helper = openDatabase(config, filesDir) ?: return@runCatching
                VaultSearchIndexer.touchPaths(workspace, helper.ensureOpen(), paths)
            }
        }.mapFailure()
    }

    override suspend fun search(
        config: WorkspaceConfig,
        filesDir: File,
        query: String,
        searchId: Long
    ): Result<SearchOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            val tokens = Fts5Query.tokenizeQuery(trimmed)
            val matchExpr = Fts5Query.buildSafeMatch(tokens)
                ?: return@runCatching SearchOutcome(
                    searchId = searchId,
                    vaultInstanceId = "",
                    notes = emptyList(),
                    status = emptyStatus()
                )
            mutex.withLock {
                val helper = openDatabase(config, filesDir)
                    ?: return@runCatching SearchOutcome(searchId, "", emptyList(), emptyStatus())
                val db = helper.ensureOpen()
                val status = VaultSearchIndexer.readStatus(db)
                val candidates = queryCandidates(db, matchExpr)
                val ranked = candidates.map { candidate ->
                    SearchRanker.rank(candidate, trimmed, tokens)
                }
                val notes = ranked
                    .map(::rankedNoteToResult)
                    .sortedWith(::compareVaultSearchNotes)
                    .take(FINAL_RESULT_MAX)
                SearchOutcome(
                    searchId = searchId,
                    vaultInstanceId = status.vaultInstanceId,
                    notes = notes,
                    status = status
                )
            }
        }.mapFailure()
    }

    override suspend fun repairIndex(config: WorkspaceConfig, filesDir: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    repairLocked(config, filesDir)
                }
            }.mapFailure()
        }

    private fun runMaintainLocked(config: WorkspaceConfig, filesDir: File) {
        val workspace = resolveWorkspace(config, filesDir).getOrThrow()
        val helper = openDatabase(config, filesDir)
            ?: throw VaultSearchException(
                com.eskerra.go.core.search.VaultSearchError.IndexOpenFailed
            )
        val baseHash = VaultSearchPathHasher.sha1Hex(workspace.canonicalPath)
        VaultSearchIndexer.maintain(workspace, helper.ensureOpen(), baseHash)
    }

    private fun repairLocked(config: WorkspaceConfig, filesDir: File) {
        val workspace = resolveWorkspace(config, filesDir).getOrThrow()
        closeHelper()
        val dbFile = VaultSearchPathHasher.indexDatabaseFile(filesDir, workspace)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        dbFile.parentFile?.mkdirs()
        openDatabase(config, filesDir)
    }

    private fun queryCandidates(
        db: android.database.sqlite.SQLiteDatabase,
        matchExpr: String
    ): List<SearchCandidate> = try {
        runQueryCandidates(db, matchExpr, useBm25 = true)
    } catch (first: SQLiteException) {
        try {
            runQueryCandidates(db, matchExpr, useBm25 = false)
        } catch (_: SQLiteException) {
            throw VaultSearchSqliteErrorMapper.map(first)
        }
    }

    private fun runQueryCandidates(
        db: android.database.sqlite.SQLiteDatabase,
        matchExpr: String,
        useBm25: Boolean
    ): List<SearchCandidate> {
        val rankExpr = if (useBm25) "bm25(vault_search_notes)" else "rank"
        val out = mutableListOf<SearchCandidate>()
        db.rawQuery(
            """
            SELECT uri, rel_path, title, filename, body, $rankExpr AS rk
            FROM vault_search_notes
            WHERE vault_search_notes MATCH ?
            ORDER BY rk
            LIMIT ?
            """.trimIndent(),
            arrayOf(matchExpr, FTS_CANDIDATE_LIMIT.toString())
        ).use { cursor ->
            val uriIndex = cursor.getColumnIndexOrThrow("uri")
            val relIndex = cursor.getColumnIndexOrThrow("rel_path")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val filenameIndex = cursor.getColumnIndexOrThrow("filename")
            val bodyIndex = cursor.getColumnIndexOrThrow("body")
            val rankIndex = cursor.getColumnIndexOrThrow("rk")
            while (cursor.moveToNext()) {
                out += SearchCandidate(
                    uri = cursor.getString(uriIndex),
                    relPath = cursor.getString(relIndex),
                    title = cursor.getString(titleIndex),
                    filename = cursor.getString(filenameIndex),
                    body = cursor.getString(bodyIndex).orEmpty(),
                    bm25 = cursor.getFloat(rankIndex)
                )
            }
        }
        return out
    }

    private fun openDatabase(config: WorkspaceConfig, filesDir: File): VaultSearchDatabase? {
        val workspace = resolveWorkspace(config, filesDir).getOrNull() ?: return null
        val key = workspace.canonicalPath
        if (openHelper != null && openWorkspaceKey == key) {
            return openHelper
        }
        closeHelper()
        val dbFile = VaultSearchPathHasher.indexDatabaseFile(filesDir, workspace)
        dbFile.parentFile?.mkdirs()
        return try {
            val helper = VaultSearchDatabase(appContext, dbFile)
            val db = helper.ensureOpen()
            val storedVersion = VaultSearchDatabase.getMeta(
                db,
                VaultSearchDatabase.KEY_SCHEMA_VERSION
            )?.toIntOrNull()
            if (storedVersion != VaultSearchDatabase.SCHEMA_VERSION) {
                helper.rebuildSchema(db)
            }
            openHelper = helper
            openWorkspaceKey = key
            helper
        } catch (error: SQLiteException) {
            throw VaultSearchSqliteErrorMapper.map(error)
        }
    }

    private fun closeHelper() {
        openHelper?.close()
        openHelper = null
        openWorkspaceKey = null
    }

    private fun resolveWorkspace(config: WorkspaceConfig, filesDir: File): Result<File> {
        val workspaceResult = WorkspacePaths.resolve(filesDir, config.relativePath)
        if (workspaceResult.isFailure) return workspaceResult
        val workspaceDir = workspaceResult.getOrThrow()
        if (!workspaceDir.isDirectory) {
            return Result.failure(
                VaultSearchException(
                    com.eskerra.go.core.search.VaultSearchError.WorkspaceUnavailable
                )
            )
        }
        return Result.success(workspaceDir)
    }

    private fun emptyStatus() = VaultSearchIndexStatus(
        vaultInstanceId = "",
        indexReady = false,
        bodiesIndexReady = false,
        indexedNotes = 0
    )

    private fun <T> Result<T>.mapFailure(): Result<T> = recoverCatching { error ->
        throw VaultSearchSqliteErrorMapper.map(error)
    }

    companion object {
        private const val FTS_CANDIDATE_LIMIT = 100
        private const val FINAL_RESULT_MAX = 150
    }
}

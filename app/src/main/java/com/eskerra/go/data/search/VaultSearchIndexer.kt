package com.eskerra.go.data.search

import com.eskerra.go.core.search.ReconcileDiffer
import java.io.File
import java.util.UUID

internal object VaultSearchIndexer {
    private const val TITLE_BATCH_SIZE = 50
    private const val BODY_BATCH_SIZE = 25
    private const val BODY_BATCHES_PER_MAINTAIN = 4

    fun ensureVaultInstance(db: VaultSearchSqlSession, basePathHash: String): String {
        val existingHash = VaultSearchDatabase.getMeta(db, VaultSearchDatabase.KEY_BASE_PATH_HASH)
        val existingId = VaultSearchDatabase.getMeta(db, VaultSearchDatabase.KEY_VAULT_INSTANCE_ID)
        if (existingHash == basePathHash && !existingId.isNullOrBlank()) {
            return existingId
        }
        val newId = UUID.randomUUID().toString()
        VaultSearchDatabase.setMeta(db, VaultSearchDatabase.KEY_BASE_PATH_HASH, basePathHash)
        VaultSearchDatabase.setMeta(db, VaultSearchDatabase.KEY_VAULT_INSTANCE_ID, newId)
        VaultSearchDatabase.setMeta(db, VaultSearchDatabase.KEY_LAST_TITLES_AT, "0")
        VaultSearchDatabase.setMeta(db, VaultSearchDatabase.KEY_LAST_FULL_BUILD_AT, "0")
        VaultSearchDatabase.setMeta(db, VaultSearchDatabase.KEY_LAST_RECONCILED_AT, "0")
        return newId
    }

    fun maintain(workspaceDir: File, db: VaultSearchSqlSession, basePathHash: String): String {
        val vaultInstanceId = ensureVaultInstance(db, basePathHash)
        val onDisk = VaultSearchWorkspaceWalker.snapshot(workspaceDir)
        val inDb = readMetaSnapshots(db)
        if (inDb.isEmpty()) {
            buildTitles(workspaceDir, db)
        } else {
            val diff = ReconcileDiffer.diff(inDb, onDisk)
            applyDiff(workspaceDir, db, diff)
        }
        fillPendingBodiesBatch(workspaceDir, db, BODY_BATCH_SIZE * BODY_BATCHES_PER_MAINTAIN)
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_RECONCILED_AT,
            System.currentTimeMillis().toString()
        )
        return vaultInstanceId
    }

    fun touchPaths(workspaceDir: File, db: VaultSearchSqlSession, paths: List<String>) {
        val onDisk = VaultSearchWorkspaceWalker.snapshot(workspaceDir)
        val touched = paths.filter { it in onDisk.keys }
        if (touched.isEmpty()) return
        val inDb = readMetaSnapshots(db)
        val subsetOnDisk = touched.associateWith { onDisk.getValue(it) }
        val subsetInDb = touched.mapNotNull { path -> inDb[path]?.let { path to it } }.toMap()
        val diff = ReconcileDiffer.diff(subsetInDb, subsetOnDisk)
        applyDiff(workspaceDir, db, diff)
        fillPendingBodiesBatch(workspaceDir, db, BODY_BATCH_SIZE * BODY_BATCHES_PER_MAINTAIN)
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_RECONCILED_AT,
            System.currentTimeMillis().toString()
        )
    }

    private fun buildTitles(workspaceDir: File, db: VaultSearchSqlSession) {
        db.execSQL("DELETE FROM vault_search_notes")
        db.execSQL("DELETE FROM note_meta")
        val entries = VaultSearchWorkspaceWalker.walk(workspaceDir)
        entries.chunked(TITLE_BATCH_SIZE).forEach { batch ->
            db.beginTransaction()
            try {
                for (entry in batch) {
                    upsertTitleOnly(db, entry)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_TITLES_AT,
            System.currentTimeMillis().toString()
        )
    }

    private fun fillPendingBodiesBatch(
        workspaceDir: File,
        db: VaultSearchSqlSession,
        maxEntries: Int
    ) {
        val pendingUris = readPendingBodyUris(db, maxEntries)
        if (pendingUris.isEmpty()) {
            markBodiesCompleteIfDone(db)
            return
        }
        val entriesByUri = VaultSearchWorkspaceWalker.walk(workspaceDir).associateBy { it.uri }
        val batch = pendingUris.mapNotNull { uri -> entriesByUri[uri] }
        if (batch.isEmpty()) {
            markBodiesCompleteIfDone(db)
            return
        }
        batch.chunked(BODY_BATCH_SIZE).forEach { chunk ->
            db.beginTransaction()
            try {
                for (entry in chunk) {
                    upsertWithBody(db, entry)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        markBodiesCompleteIfDone(db)
    }

    private fun readPendingBodyUris(db: VaultSearchSqlSession, limit: Int): List<String> {
        val out = mutableListOf<String>()
        db.rawQuery(
            "SELECT uri FROM note_meta WHERE body_indexed = 0 LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            val uriIndex = cursor.getColumnIndexOrThrow("uri")
            while (cursor.moveToNext()) {
                out += cursor.getString(uriIndex)
            }
        }
        return out
    }

    private fun markBodiesCompleteIfDone(db: VaultSearchSqlSession) {
        val pending = db.rawQuery(
            "SELECT COUNT(*) FROM note_meta WHERE body_indexed = 0",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) 0 else cursor.getInt(0)
        }
        if (pending == 0) {
            VaultSearchDatabase.setMeta(
                db,
                VaultSearchDatabase.KEY_LAST_FULL_BUILD_AT,
                System.currentTimeMillis().toString()
            )
        }
    }

    private fun applyDiff(
        workspaceDir: File,
        db: VaultSearchSqlSession,
        diff: com.eskerra.go.core.search.ReconcileDiffResult
    ) {
        if (diff.removed.isEmpty() && diff.added.isEmpty() && diff.updated.isEmpty()) return
        db.beginTransaction()
        try {
            for (uri in diff.removed) {
                removeUri(db, uri)
            }
            val entriesByUri = VaultSearchWorkspaceWalker.walk(workspaceDir).associateBy { it.uri }
            for (uri in diff.added) {
                entriesByUri[uri]?.let { upsertTitleOnly(db, it) }
            }
            for (uri in diff.updated) {
                entriesByUri[uri]?.let { upsertTitleOnly(db, it) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun removeUri(db: VaultSearchSqlSession, uri: String) {
        val rowid = readFtsRowid(db, uri)
        if (rowid != null) {
            db.execSQL("DELETE FROM vault_search_notes WHERE rowid = ?", arrayOf(rowid))
        }
        db.execSQL("DELETE FROM note_meta WHERE uri = ?", arrayOf(uri))
    }

    private fun readFtsRowid(db: VaultSearchSqlSession, uri: String): Long? {
        db.rawQuery("SELECT fts_rowid FROM note_meta WHERE uri = ?", arrayOf(uri)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val rowid = cursor.getLong(0)
            return rowid.takeIf { it > 0L }
        }
    }

    private fun upsertTitleOnly(db: VaultSearchSqlSession, entry: VaultSearchFileEntry) {
        removeUri(db, entry.uri)
        val rowid = insertFtsRow(
            db,
            entry.uri,
            entry.relPath,
            entry.title,
            entry.filename,
            ""
        )
        db.execSQL(
            """
            INSERT INTO note_meta(
              uri, rel_path, filename, title, size, last_modified, body_indexed, fts_rowid
            )
            VALUES(?, ?, ?, ?, ?, ?, 0, ?)
            """.trimIndent(),
            arrayOf(
                entry.uri,
                entry.relPath,
                entry.filename,
                entry.title,
                entry.size,
                entry.lastModified,
                rowid
            )
        )
    }

    private fun upsertWithBody(db: VaultSearchSqlSession, entry: VaultSearchFileEntry) {
        removeUri(db, entry.uri)
        val rowid = insertFtsRow(
            db,
            entry.uri,
            entry.relPath,
            entry.title,
            entry.filename,
            entry.body
        )
        db.execSQL(
            """
            INSERT INTO note_meta(
              uri, rel_path, filename, title, size, last_modified, body_indexed, fts_rowid
            )
            VALUES(?, ?, ?, ?, ?, ?, 1, ?)
            ON CONFLICT(uri) DO UPDATE SET
              rel_path = excluded.rel_path,
              filename = excluded.filename,
              title = excluded.title,
              size = excluded.size,
              last_modified = excluded.last_modified,
              body_indexed = 1,
              fts_rowid = excluded.fts_rowid
            """.trimIndent(),
            arrayOf(
                entry.uri,
                entry.relPath,
                entry.filename,
                entry.title,
                entry.size,
                entry.lastModified,
                rowid
            )
        )
    }

    private fun insertFtsRow(
        db: VaultSearchSqlSession,
        uri: String,
        relPath: String,
        title: String,
        filename: String,
        body: String
    ): Long {
        val statement = db.compileStatement(
            """
            INSERT INTO vault_search_notes(uri, rel_path, title, filename, body)
            VALUES(?, ?, ?, ?, ?)
            """.trimIndent()
        )
        statement.use {
            it.bindString(1, uri)
            it.bindString(2, relPath)
            it.bindString(3, title)
            it.bindString(4, filename)
            it.bindString(5, body)
            return it.executeInsert()
        }
    }

    private fun readMetaSnapshots(
        db: VaultSearchSqlSession
    ): Map<String, com.eskerra.go.core.search.FileSnapshot> {
        val out = linkedMapOf<String, com.eskerra.go.core.search.FileSnapshot>()
        db.rawQuery("SELECT uri, size, last_modified FROM note_meta", null).use { cursor ->
            val uriIndex = cursor.getColumnIndexOrThrow("uri")
            val sizeIndex = cursor.getColumnIndexOrThrow("size")
            val modifiedIndex = cursor.getColumnIndexOrThrow("last_modified")
            while (cursor.moveToNext()) {
                out[cursor.getString(uriIndex)] = com.eskerra.go.core.search.FileSnapshot(
                    size = cursor.getLong(sizeIndex),
                    lastModified = cursor.getLong(modifiedIndex)
                )
            }
        }
        return out
    }

    fun readStatus(db: VaultSearchSqlSession): com.eskerra.go.core.search.VaultSearchIndexStatus {
        val titlesAt =
            VaultSearchDatabase.getMeta(db, VaultSearchDatabase.KEY_LAST_TITLES_AT)?.toLongOrNull()
                ?: 0L
        val bodiesAt =
            VaultSearchDatabase.getMeta(
                db,
                VaultSearchDatabase.KEY_LAST_FULL_BUILD_AT
            )?.toLongOrNull()
                ?: 0L
        val vaultInstanceId = VaultSearchDatabase.getMeta(
            db,
            VaultSearchDatabase.KEY_VAULT_INSTANCE_ID
        ).orEmpty()
        val indexedNotes = db.rawQuery("SELECT COUNT(*) FROM note_meta", null).use { cursor ->
            if (!cursor.moveToFirst()) 0 else cursor.getInt(0)
        }
        return com.eskerra.go.core.search.VaultSearchIndexStatus(
            vaultInstanceId = vaultInstanceId,
            indexReady = titlesAt > 0L,
            bodiesIndexReady = bodiesAt > 0L,
            indexedNotes = indexedNotes
        )
    }
}

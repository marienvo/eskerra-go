package com.eskerra.go.data.search

import android.database.sqlite.SQLiteDatabase
import com.eskerra.go.core.search.ReconcileDiffer
import java.io.File
import java.util.UUID

internal object VaultSearchIndexer {
    private const val TITLE_BATCH_SIZE = 50
    private const val BODY_BATCH_SIZE = 25

    fun ensureVaultInstance(db: SQLiteDatabase, basePathHash: String): String {
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

    fun maintain(workspaceDir: File, db: SQLiteDatabase, basePathHash: String): String {
        val vaultInstanceId = ensureVaultInstance(db, basePathHash)
        val onDisk = VaultSearchWorkspaceWalker.snapshot(workspaceDir)
        val inDb = readMetaSnapshots(db)
        if (inDb.isEmpty()) {
            fullBuild(workspaceDir, db)
        } else {
            val diff = ReconcileDiffer.diff(inDb, onDisk)
            applyDiff(workspaceDir, db, diff)
        }
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_RECONCILED_AT,
            System.currentTimeMillis().toString()
        )
        return vaultInstanceId
    }

    fun touchPaths(workspaceDir: File, db: SQLiteDatabase, paths: List<String>) {
        val onDisk = VaultSearchWorkspaceWalker.snapshot(workspaceDir)
        val touched = paths.filter { it in onDisk.keys }
        if (touched.isEmpty()) return
        val inDb = readMetaSnapshots(db)
        val subsetOnDisk = touched.associateWith { onDisk.getValue(it) }
        val subsetInDb = touched.mapNotNull { path -> inDb[path]?.let { path to it } }.toMap()
        val diff = ReconcileDiffer.diff(subsetInDb, subsetOnDisk)
        applyDiff(workspaceDir, db, diff)
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_RECONCILED_AT,
            System.currentTimeMillis().toString()
        )
    }

    private fun fullBuild(workspaceDir: File, db: SQLiteDatabase) {
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
        fillBodies(workspaceDir, db, entries.map { it.uri }.toSet())
    }

    private fun fillBodies(workspaceDir: File, db: SQLiteDatabase, onlyUris: Set<String>? = null) {
        val entries = VaultSearchWorkspaceWalker.walk(workspaceDir)
            .filter { onlyUris == null || it.uri in onlyUris }
        entries.chunked(BODY_BATCH_SIZE).forEach { batch ->
            db.beginTransaction()
            try {
                for (entry in batch) {
                    upsertWithBody(db, entry)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        VaultSearchDatabase.setMeta(
            db,
            VaultSearchDatabase.KEY_LAST_FULL_BUILD_AT,
            System.currentTimeMillis().toString()
        )
    }

    private fun applyDiff(
        workspaceDir: File,
        db: SQLiteDatabase,
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
        val bodyTargets = (diff.added + diff.updated).toSet()
        if (bodyTargets.isNotEmpty()) {
            fillBodies(workspaceDir, db, bodyTargets)
        }
    }

    private fun removeUri(db: SQLiteDatabase, uri: String) {
        db.execSQL("DELETE FROM note_meta WHERE uri = ?", arrayOf(uri))
        db.execSQL("DELETE FROM vault_search_notes WHERE uri = ?", arrayOf(uri))
    }

    private fun upsertTitleOnly(db: SQLiteDatabase, entry: VaultSearchFileEntry) {
        db.execSQL("DELETE FROM vault_search_notes WHERE uri = ?", arrayOf(entry.uri))
        db.execSQL(
            """
            INSERT INTO vault_search_notes(uri, rel_path, title, filename, body)
            VALUES(?, ?, ?, ?, '')
            """.trimIndent(),
            arrayOf(entry.uri, entry.relPath, entry.title, entry.filename)
        )
        db.execSQL(
            """
            INSERT INTO note_meta(uri, rel_path, filename, title, size, last_modified, body_indexed)
            VALUES(?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(uri) DO UPDATE SET
              rel_path = excluded.rel_path,
              filename = excluded.filename,
              title = excluded.title,
              size = excluded.size,
              last_modified = excluded.last_modified,
              body_indexed = 0
            """.trimIndent(),
            arrayOf(
                entry.uri,
                entry.relPath,
                entry.filename,
                entry.title,
                entry.size,
                entry.lastModified
            )
        )
    }

    private fun upsertWithBody(db: SQLiteDatabase, entry: VaultSearchFileEntry) {
        db.execSQL("DELETE FROM vault_search_notes WHERE uri = ?", arrayOf(entry.uri))
        db.execSQL(
            """
            INSERT INTO vault_search_notes(uri, rel_path, title, filename, body)
            VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(entry.uri, entry.relPath, entry.title, entry.filename, entry.body)
        )
        db.execSQL(
            """
            INSERT INTO note_meta(uri, rel_path, filename, title, size, last_modified, body_indexed)
            VALUES(?, ?, ?, ?, ?, ?, 1)
            ON CONFLICT(uri) DO UPDATE SET
              rel_path = excluded.rel_path,
              filename = excluded.filename,
              title = excluded.title,
              size = excluded.size,
              last_modified = excluded.last_modified,
              body_indexed = 1
            """.trimIndent(),
            arrayOf(
                entry.uri,
                entry.relPath,
                entry.filename,
                entry.title,
                entry.size,
                entry.lastModified
            )
        )
    }

    private fun readMetaSnapshots(
        db: SQLiteDatabase
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

    fun readStatus(db: SQLiteDatabase): com.eskerra.go.core.search.VaultSearchIndexStatus {
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

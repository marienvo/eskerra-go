package com.eskerra.go.data.search

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Vault search index database backed by [BundledSQLiteDriver], which ships SQLite with FTS5.
 * The Android framework SQLite build often omits FTS5 (`no such module: fts5`).
 */
internal class VaultSearchDatabase(
    private val appContext: Context,
    private val databaseFile: File
) {
    private val driver: BundledSQLiteDriver = BundledSQLiteDriver()
    private var connection: SQLiteConnection? = null

    fun ensureOpen(): VaultSearchSqlSession {
        val openConnection = connection ?: openConnection().also { connection = it }
        ensureSchema(openConnection)
        return VaultSearchSqlSession(openConnection)
    }

    fun close() {
        connection?.close()
        connection = null
    }

    fun rebuildSchema(session: VaultSearchSqlSession = ensureOpen()) {
        session.execSQL("DROP TABLE IF EXISTS vault_search_notes")
        session.execSQL("DROP TABLE IF EXISTS note_meta")
        session.execSQL("DROP TABLE IF EXISTS index_meta")
        createTables(session, appContext)
        setMeta(session, KEY_SCHEMA_VERSION, SCHEMA_VERSION.toString())
    }

    private fun openConnection(): SQLiteConnection {
        databaseFile.parentFile?.mkdirs()
        return driver.open(databaseFile.absolutePath)
    }

    private fun ensureSchema(connection: SQLiteConnection) {
        val session = VaultSearchSqlSession(connection)
        if (!hasTable(session, INDEX_META_TABLE)) {
            bootstrapSchema(session)
            return
        }
        val storedVersion = getMeta(session, KEY_SCHEMA_VERSION)?.toIntOrNull()
        if (storedVersion == null || storedVersion != SCHEMA_VERSION) {
            rebuildSchema(session)
        }
    }

    private fun bootstrapSchema(session: VaultSearchSqlSession) {
        createTables(session, appContext)
        setMeta(session, KEY_SCHEMA_VERSION, SCHEMA_VERSION.toString())
    }

    companion object {
        private const val INDEX_META_TABLE = "index_meta"

        fun hasTable(session: VaultSearchSqlSession, tableName: String): Boolean {
            session.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tableName)
            ).use { cursor -> return cursor.moveToFirst() }
        }

        fun getMeta(session: VaultSearchSqlSession, key: String): String? {
            if (!hasTable(session, INDEX_META_TABLE)) return null
            session.rawQuery("SELECT v FROM index_meta WHERE k = ?", arrayOf(key)).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return cursor.getString(0)
            }
        }

        fun setMeta(session: VaultSearchSqlSession, key: String, value: String) {
            session.execSQL(
                "INSERT INTO index_meta(k, v) VALUES(?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v",
                arrayOf(key, value)
            )
        }

        const val SCHEMA_VERSION = 5
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_BASE_PATH_HASH = "base_path_hash"
        const val KEY_VAULT_INSTANCE_ID = "vault_instance_id"
        const val KEY_LAST_TITLES_AT = "last_titles_at"
        const val KEY_LAST_FULL_BUILD_AT = "last_full_build_at"
        const val KEY_LAST_RECONCILED_AT = "last_reconciled_at"
        const val KEY_FTS_TOKENIZER = "fts_tokenizer"

        fun createTables(session: VaultSearchSqlSession, context: Context) {
            session.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_meta(
                  uri TEXT PRIMARY KEY,
                  rel_path TEXT NOT NULL,
                  filename TEXT NOT NULL,
                  title TEXT NOT NULL,
                  size INTEGER NOT NULL,
                  last_modified INTEGER NOT NULL,
                  body_indexed INTEGER NOT NULL DEFAULT 0,
                  fts_rowid INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
            session.execSQL(
                """
                CREATE TABLE IF NOT EXISTS index_meta(
                  k TEXT PRIMARY KEY,
                  v TEXT NOT NULL
                );
                """.trimIndent()
            )
            session.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_note_meta_rel_path ON note_meta(rel_path);"
            )
            createFtsTable(session, context)
        }

        private fun createFtsTable(session: VaultSearchSqlSession, context: Context) {
            session.execSQL("DROP TABLE IF EXISTS vault_search_notes")
            val candidates = VaultSearchTokenizer.clauseCandidates(context)
            var lastError: SQLiteException? = null
            for (tokenize in candidates) {
                try {
                    session.execSQL(
                        """
                        CREATE VIRTUAL TABLE vault_search_notes USING fts5(
                          uri UNINDEXED,
                          rel_path,
                          title,
                          filename,
                          body,
                          tokenize = '$tokenize'
                        );
                        """.trimIndent()
                    )
                    setMeta(session, KEY_FTS_TOKENIZER, tokenize)
                    return
                } catch (error: SQLiteException) {
                    lastError = error
                    session.execSQL("DROP TABLE IF EXISTS vault_search_notes")
                }
            }
            throw lastError ?: SQLiteException("Failed to create vault_search_notes FTS table")
        }
    }
}

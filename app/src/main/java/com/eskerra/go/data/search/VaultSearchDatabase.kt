package com.eskerra.go.data.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

internal class VaultSearchDatabase(context: Context, databaseFile: File) :
    SQLiteOpenHelper(context, databaseFile.absolutePath, null, SCHEMA_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
        setMeta(db, KEY_SCHEMA_VERSION, SCHEMA_VERSION.toString())
    }

    fun setMeta(db: SQLiteDatabase, key: String, value: String) = Companion.setMeta(db, key, value)

    fun getMeta(db: SQLiteDatabase, key: String): String? = Companion.getMeta(db, key)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion) {
            rebuildSchema(db)
        }
    }

    fun ensureOpen(): SQLiteDatabase = writableDatabase

    fun rebuildSchema(db: SQLiteDatabase = writableDatabase) {
        db.execSQL("DROP TABLE IF EXISTS vault_search_notes")
        db.execSQL("DROP TABLE IF EXISTS note_meta")
        db.execSQL("DROP TABLE IF EXISTS index_meta")
        createTables(db)
        setMeta(db, KEY_SCHEMA_VERSION, SCHEMA_VERSION.toString())
    }

    companion object {
        fun getMeta(db: SQLiteDatabase, key: String): String? {
            db.rawQuery("SELECT v FROM index_meta WHERE k = ?", arrayOf(key)).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return cursor.getString(0)
            }
        }

        fun setMeta(db: SQLiteDatabase, key: String, value: String) {
            db.execSQL(
                "INSERT INTO index_meta(k, v) VALUES(?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v",
                arrayOf(key, value)
            )
        }

        const val SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_BASE_PATH_HASH = "base_path_hash"
        const val KEY_VAULT_INSTANCE_ID = "vault_instance_id"
        const val KEY_LAST_TITLES_AT = "last_titles_at"
        const val KEY_LAST_FULL_BUILD_AT = "last_full_build_at"
        const val KEY_LAST_RECONCILED_AT = "last_reconciled_at"

        fun createTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS vault_search_notes USING fts5(
                  uri UNINDEXED,
                  rel_path,
                  title,
                  filename,
                  body,
                  tokenize = 'unicode61 remove_diacritics 2'
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_meta(
                  uri TEXT PRIMARY KEY,
                  rel_path TEXT NOT NULL,
                  filename TEXT NOT NULL,
                  title TEXT NOT NULL,
                  size INTEGER NOT NULL,
                  last_modified INTEGER NOT NULL,
                  body_indexed INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS index_meta(
                  k TEXT PRIMARY KEY,
                  v TEXT NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_meta_rel_path ON note_meta(rel_path);")
        }
    }
}

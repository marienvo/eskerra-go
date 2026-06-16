package com.eskerra.go.data.search

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Thin SQLite access layer for vault search. Uses [androidx.sqlite.SQLiteConnection]
 * (bundled SQLite driver) instead of the framework [android.database.sqlite.SQLiteDatabase],
 * because many devices ship SQLite without the FTS5 module.
 */
internal class VaultSearchSqlSession(private val connection: SQLiteConnection) {
    private var transactionDepth = 0
    private var transactionSuccessful = false

    fun execSQL(sql: String, bindArgs: Array<out Any?>? = null) {
        connection.prepare(sql).use { statement ->
            bindArgs?.forEachIndexed { index, value ->
                bindValue(statement, index + 1, value)
            }
            drainStatement(statement)
        }
    }

    fun rawQuery(sql: String, bindArgs: Array<String>?): VaultSearchSqlCursor {
        val statement = connection.prepare(sql)
        bindArgs?.forEachIndexed { index, value ->
            statement.bindText(index + 1, value)
        }
        return VaultSearchSqlCursor(statement)
    }

    fun compileStatement(sql: String): VaultSearchCompiledStatement =
        VaultSearchCompiledStatement(connection, sql)

    fun beginTransaction() {
        if (transactionDepth == 0) {
            execSQL("BEGIN IMMEDIATE")
            transactionSuccessful = false
        }
        transactionDepth++
    }

    fun setTransactionSuccessful() {
        transactionSuccessful = true
    }

    fun endTransaction() {
        if (transactionDepth == 0) return
        transactionDepth--
        if (transactionDepth == 0) {
            if (transactionSuccessful) {
                execSQL("COMMIT")
            } else {
                execSQL("ROLLBACK")
            }
        }
    }

    private fun drainStatement(statement: SQLiteStatement) {
        while (statement.step()) {
            // Discard rows for non-query statements that may return result sets.
        }
    }

    private fun bindValue(statement: SQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.bindNull(index)
            is String -> statement.bindText(index, value)
            is Long -> statement.bindLong(index, value)
            is Int -> statement.bindLong(index, value.toLong())
            else -> statement.bindText(index, value.toString())
        }
    }
}

internal class VaultSearchSqlCursor(private val statement: SQLiteStatement) : AutoCloseable {
    private var beforeFirst = true

    fun moveToFirst(): Boolean {
        beforeFirst = false
        return statement.step()
    }

    fun moveToNext(): Boolean {
        if (beforeFirst) return moveToFirst()
        return statement.step()
    }

    fun getColumnIndexOrThrow(name: String): Int {
        val count = statement.getColumnCount()
        for (index in 0 until count) {
            if (statement.getColumnName(index).equals(name, ignoreCase = true)) {
                return index
            }
        }
        throw IllegalArgumentException("Column '$name' not found")
    }

    fun getString(columnIndex: Int): String = statement.getText(columnIndex)

    fun getLong(columnIndex: Int): Long = statement.getLong(columnIndex)

    fun getInt(columnIndex: Int): Int = statement.getLong(columnIndex).toInt()

    fun getFloat(columnIndex: Int): Float = statement.getDouble(columnIndex).toFloat()

    override fun close() {
        statement.close()
    }
}

internal class VaultSearchCompiledStatement(
    private val connection: SQLiteConnection,
    private val sql: String
) : AutoCloseable {
    private val statement: SQLiteStatement = connection.prepare(sql)

    fun bindString(index: Int, value: String) {
        statement.bindText(index, value)
    }

    fun executeInsert(): Long {
        statement.step()
        return connection.prepare("SELECT last_insert_rowid()").use { rowIdStatement ->
            check(rowIdStatement.step()) { "last_insert_rowid() returned no row" }
            rowIdStatement.getLong(0)
        }
    }

    override fun close() {
        statement.close()
    }
}

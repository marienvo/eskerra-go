package com.eskerra.go.data.search

import android.database.sqlite.SQLiteException
import com.eskerra.go.core.search.VaultSearchError
import com.eskerra.go.core.search.VaultSearchException

internal object VaultSearchSqliteErrorMapper {
    fun map(throwable: Throwable): VaultSearchException {
        if (throwable is VaultSearchException) return throwable
        val text = throwable.message.orEmpty().lowercase()
        val error = when {
            text.contains("no such module: fts5") ||
                text.contains("unknown tokenizer") ||
                text.contains("fts5") &&
                text.contains("not supported") ->
                VaultSearchError.Fts5Unsupported

            text.contains("database disk image is malformed") ||
                text.contains("file is not a database") ||
                text.contains("corrupt") ->
                VaultSearchError.IndexCorrupt

            text.contains("no such table: vault_search_notes") ||
                text.contains("malformed") &&
                text.contains("fts") ->
                VaultSearchError.IndexCorrupt

            text.contains("malformed match") ||
                text.contains("syntax error") &&
                text.contains("match") ->
                VaultSearchError.QueryFailed

            text.contains("unable to open") ||
                text.contains("readonly database") ||
                text.contains("search db unavailable") ->
                VaultSearchError.IndexOpenFailed

            text.contains("workspace missing") ||
                text.contains("workspace is not available") ->
                VaultSearchError.WorkspaceUnavailable

            else -> VaultSearchError.Unknown("Search is unavailable.")
        }
        return VaultSearchException(error)
    }

    fun wrap(block: () -> Unit) {
        try {
            block()
        } catch (e: SQLiteException) {
            throw map(e)
        } catch (e: IllegalStateException) {
            throw map(e)
        }
    }
}

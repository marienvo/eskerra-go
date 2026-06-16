package com.eskerra.go.data.search

import android.util.Log
import androidx.sqlite.SQLiteException
import com.eskerra.go.core.search.VaultSearchError
import com.eskerra.go.core.search.VaultSearchException

internal object VaultSearchSqliteErrorMapper {
    private const val TAG = "VaultSearch"

    fun map(throwable: Throwable): VaultSearchException {
        if (throwable is VaultSearchException) return throwable
        val text = messageChain(throwable)
        val error = when {
            text.contains("no such module: fts5") ->
                VaultSearchError.Fts5Unsupported

            isTokenizerFailure(text) ->
                VaultSearchError.IndexCorrupt

            text.contains("database disk image is malformed") ||
                text.contains("file is not a database") ||
                text.contains("corrupt") ->
                VaultSearchError.IndexCorrupt

            text.contains("no such table: vault_search_notes") ||
                text.contains("no such table: index_meta") ||
                text.contains("no such table: note_meta") ||
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
        runCatching { Log.e(TAG, "Mapped search error: ${diagnosticDetail(throwable)}", throwable) }
        return VaultSearchException(error, throwable, diagnosticDetail(throwable))
    }

    fun diagnosticDetail(throwable: Throwable): String? {
        val message = generateSequence(throwable) { it.cause }
            .mapNotNull { it.message?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        return message.take(DIAGNOSTIC_MAX_LENGTH)
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

    private fun isTokenizerFailure(text: String): Boolean = text.contains("unknown tokenizer") ||
        text.contains("unknown tokenizer option") ||
        (text.contains("tokenize") && text.contains("not supported")) ||
        (text.contains("remove_diacritics") && text.contains("not supported")) ||
        (text.contains("unicode61") && text.contains("not supported"))

    private fun messageChain(throwable: Throwable): String =
        generateSequence(throwable) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf { message -> message.isNotEmpty() } }
            .joinToString(" | ")
            .lowercase()

    private const val DIAGNOSTIC_MAX_LENGTH = 200
}

package com.eskerra.go.data.search

import android.content.Context
import android.os.Build

/**
 * FTS5 tokenizer for vault search (spec §12).
 *
 * Preferred option uses `remove_diacritics`; [clauseCandidates] lists fallbacks used when
 * creating the FTS table. With [BundledSQLiteDriver], try mode 2 on all SDK levels first.
 */
internal object VaultSearchTokenizer {
    fun clause(context: Context): String = clauseForSdk(Build.VERSION.SDK_INT)

    fun clauseForSdk(sdkInt: Int): String = clauseCandidatesForSdk(sdkInt).first()

    fun clauseCandidates(context: Context): List<String> =
        clauseCandidatesForSdk(Build.VERSION.SDK_INT)

    fun clauseCandidatesForSdk(sdkInt: Int): List<String> = buildList {
        add(FTS_TOKENIZE_API_30_PLUS)
        add(FTS_TOKENIZE_LEGACY)
        add(FTS_TOKENIZE_UNICODE61)
        add(FTS_TOKENIZE_PORTER)
    }

    const val FTS_TOKENIZE_API_30_PLUS = "unicode61 remove_diacritics 2"
    const val FTS_TOKENIZE_LEGACY = "unicode61 remove_diacritics 1"
    const val FTS_TOKENIZE_UNICODE61 = "unicode61"
    const val FTS_TOKENIZE_PORTER = "porter"
}

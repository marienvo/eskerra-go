package com.eskerra.go.core.repository

/**
 * Reads the HTML `<title>` of a web page.
 *
 * Every failure — offline, timeout, non-2xx, non-HTML content, no title in the part of the
 * document we are willing to read — resolves to `null`. Implementations never throw: a share
 * whose title cannot be fetched simply keeps the draft the user can already fill in.
 */
fun interface PageTitleFetcher {
    suspend fun fetchTitle(url: String): String?
}

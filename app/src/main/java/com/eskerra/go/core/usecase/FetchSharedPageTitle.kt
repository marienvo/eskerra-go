package com.eskerra.go.core.usecase

import com.eskerra.go.core.repository.PageTitleFetcher
import com.eskerra.go.core.share.ShareTitleText

/**
 * Fetches a shared URL's page title, already sanitized into something that may serve as line 1
 * of an inbox draft (and therefore as the note's filename). Null whenever no usable title exists.
 */
class FetchSharedPageTitle(private val fetcher: PageTitleFetcher) {

    suspend operator fun invoke(url: String): String? =
        fetcher.fetchTitle(url)?.let(ShareTitleText::sanitizeTitleLine)
}

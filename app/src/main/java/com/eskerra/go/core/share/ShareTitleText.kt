package com.eskerra.go.core.share

/**
 * Turns a candidate title (an `EXTRA_SUBJECT`, or a page `<title>`) into a single line that is
 * safe to use as line 1 of an inbox draft — which is also the note's filename stem.
 *
 * The length cap is load-bearing: `InboxMarkdownFileName.sanitizeFileName` has no limit of its
 * own, so an unbounded page title would produce an unusable filename.
 */
object ShareTitleText {

    const val MAX_TITLE_LENGTH = 120

    private const val H1_PREFIX = "# "

    /** A one-line title, or null when the candidate is unusable (blank, or a URL itself). */
    fun sanitizeTitleLine(raw: String?): String? {
        if (raw == null) {
            return null
        }
        val collapsed = raw
            .map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val withoutH1 = collapsed.removePrefix(H1_PREFIX).trim()
        if (withoutH1.isEmpty() || SharedUrl.looksLikeUrl(withoutH1)) {
            return null
        }
        return truncate(withoutH1)
    }

    private fun truncate(title: String): String {
        if (title.length <= MAX_TITLE_LENGTH) {
            return title
        }
        val hardCut = title.take(MAX_TITLE_LENGTH)
        val lastSpace = hardCut.lastIndexOf(' ')
        val cut = if (lastSpace >= MAX_TITLE_LENGTH / 2) hardCut.take(lastSpace) else hardCut
        return cut.trimEnd()
    }
}

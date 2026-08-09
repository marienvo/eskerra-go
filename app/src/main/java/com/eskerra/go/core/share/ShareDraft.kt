package com.eskerra.go.core.share

/**
 * Compose-pill text for an inbound share, plus where the caret belongs.
 *
 * [caretOffset] is always on line 1 — either at the end of a title we already know, or at
 * offset 0 on an empty title line the user still has to fill in. [titleFetchUrl] is non-null
 * only for the second shape: it names the URL whose `<title>` would upgrade the draft.
 */
data class ShareDraft(val text: String, val caretOffset: Int, val titleFetchUrl: String? = null)

/**
 * Builds the immediate draft for a share. Two shapes, both directly usable:
 *
 * - **title known** (a usable `EXTRA_SUBJECT`) → `"<title>\n\n<shared>"`, caret after the title,
 *   no network work at all;
 * - **title unknown** → `"\n\n<shared>"` with the caret on the empty line 1, so the user types
 *   the title. For a bare URL this shape is also the fetch candidate — a fetched title is a pure
 *   upgrade on top of an already-correct draft, which is why a failed or slow fetch needs no
 *   fallback path.
 */
object BuildShareDraft {

    const val MAX_SHARED_BODY_CHARS = 20_000

    private const val TRUNCATION_MARKER = "…"

    /** Null when nothing usable was shared. */
    fun build(shared: SharedContent): ShareDraft? {
        val body = capBody(shared.text.replace("\r\n", "\n").trim())
        val subject = ShareTitleText.sanitizeTitleLine(shared.subject)?.takeIf { it != body }

        return when {
            body.isEmpty() && subject == null -> null
            body.isEmpty() -> ShareDraft(subject.orEmpty(), subject.orEmpty().length)
            subject != null -> ShareDraft("$subject\n\n$body", subject.length)
            else -> ShareDraft("\n\n$body", 0, SharedUrl.soleUrlOrNull(body))
        }
    }

    private fun capBody(body: String): String = if (body.length <= MAX_SHARED_BODY_CHARS) {
        body
    } else {
        body.take(MAX_SHARED_BODY_CHARS).trimEnd() + TRUNCATION_MARKER
    }
}

/**
 * Applies a fetched page title to a draft built by [BuildShareDraft] in its title-unknown shape.
 * Returns null when the title is unusable or the draft is not that shape (line 1 must be empty —
 * anything else means the title line is already taken, and the user's text always wins).
 */
object ShareTitleUpgrade {

    fun apply(immediateText: String, rawTitle: String): ShareDraft? {
        val title = ShareTitleText.sanitizeTitleLine(rawTitle) ?: return null
        if (!immediateText.startsWith("\n")) {
            return null
        }
        return ShareDraft(title + immediateText, title.length)
    }
}

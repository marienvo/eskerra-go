package com.eskerra.go.core.share

/**
 * What an inbound share turned out to be. Kept platform-neutral so the decision is a plain unit
 * test rather than an instrumented one — `SharedIntentReader` only extracts the fields.
 */
sealed interface ShareRead {

    /**
     * Usable text arrived. [ignoredAttachment] is true when binary came along with it (an image
     * share that also carries a link): the note is built from the text alone.
     */
    data class Content(val content: SharedContent, val ignoredAttachment: Boolean) : ShareRead

    /** Only binary arrived — nothing to build a note from. */
    data object AttachmentOnly : ShareRead

    /** Nothing to do: wrong action, unsupported type, already consumed, or empty. */
    data object None : ShareRead
}

/**
 * Classifies a share from the extras alone. Images are accepted so that senders which package a
 * link as a picture (ChatGPT's thread share, for one) still reach the composer, but the binary
 * itself is never read — only [text] and [subject] become a note.
 */
fun classifyShare(
    isSendAction: Boolean,
    type: String?,
    text: String?,
    subject: String?,
    hasAttachment: Boolean,
    alreadyConsumed: Boolean
): ShareRead {
    if (!isSendAction || alreadyConsumed) {
        return ShareRead.None
    }
    // Defense in depth beside the manifest filter: an explicit intent could name any type.
    if (type != null && !type.startsWith("text/") && !type.startsWith("image/")) {
        return ShareRead.None
    }
    if (!text.isNullOrBlank() || !subject.isNullOrBlank()) {
        return ShareRead.Content(
            content = SharedContent(text = text.orEmpty(), subject = subject),
            ignoredAttachment = hasAttachment
        )
    }
    if (hasAttachment) {
        return ShareRead.AttachmentOnly
    }
    return ShareRead.None
}

package com.eskerra.go.core.share

/**
 * One-shot instruction to seed the compose pill from a share.
 *
 * [token] is the originating [PendingShare.id]: it is what makes applying idempotent, and what
 * lets a late [Stage.TitleUpgrade] recognize that it belongs to the draft currently on screen.
 */
data class SharePrefill(val token: Long, val stage: Stage, val text: String, val caretOffset: Int) {
    enum class Stage {
        /** The draft available without any network work. */
        Immediate,

        /** A fetched page title raised onto line 1 of an already-applied [Immediate] draft. */
        TitleUpgrade
    }
}

/**
 * Decides how an incoming share meets whatever is already in the pill. A share never destroys
 * text the user typed: it either takes an empty draft, or it appends below what is there.
 */
object SharePrefillMerge {

    data class Merged(
        val text: String,
        val caretOffset: Int,
        /**
         * True when the prefill took the draft over completely. Only then does line 1 belong to
         * the share, so only then may a later title upgrade rewrite it.
         */
        val replacedDraft: Boolean
    )

    fun mergeImmediate(currentDraft: String, prefill: SharePrefill): Merged {
        if (currentDraft.isBlank()) {
            return Merged(prefill.text, prefill.caretOffset, replacedDraft = true)
        }
        val appended = currentDraft.trimEnd() + "\n\n" + prefill.text.trim()
        return Merged(appended, appended.length, replacedDraft = false)
    }
}

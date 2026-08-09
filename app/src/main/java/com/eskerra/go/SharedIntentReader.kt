package com.eskerra.go

import android.content.Intent
import com.eskerra.go.core.share.SharedContent

/**
 * The one place an `Intent` is turned into share data. Everything below this works on
 * [SharedContent], which keeps the share pipeline unit-testable.
 */
object SharedIntentReader {

    /** Marks an intent whose share was already taken, so a re-read cannot duplicate it. */
    const val EXTRA_CONSUMED = "com.eskerra.go.SHARE_CONSUMED"

    fun read(intent: Intent?): SharedContent? {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            return null
        }
        val type = intent.type
        if (type != null && !type.startsWith("text/")) {
            return null
        }
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) {
            return null
        }

        // getCharSequenceExtra, not getStringExtra: several senders put a styled Spanned here,
        // and getStringExtra returns null for those.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = (
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)
            )?.toString()

        if (text.isBlank() && subject.isNullOrBlank()) {
            return null
        }
        return SharedContent(text = text, subject = subject)
    }
}

package com.eskerra.go

import android.content.Intent
import com.eskerra.go.core.share.ShareRead
import com.eskerra.go.core.share.classifyShare

/**
 * The one place an `Intent` is turned into share data. Everything below this works on
 * [com.eskerra.go.core.share.SharedContent], which keeps the share pipeline unit-testable.
 */
object SharedIntentReader {

    /** Marks an intent whose share was already taken, so a re-read cannot duplicate it. */
    const val EXTRA_CONSUMED = "com.eskerra.go.SHARE_CONSUMED"

    fun read(intent: Intent?): ShareRead {
        if (intent == null) {
            return ShareRead.None
        }

        // getCharSequenceExtra, not getStringExtra: several senders put a styled Spanned here,
        // and getStringExtra returns null for those.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = (
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)
            )?.toString()

        return classifyShare(
            isSendAction = intent.action == Intent.ACTION_SEND,
            type = intent.type,
            text = text,
            subject = subject,
            // Presence only. The URI is deliberately never resolved: no content resolver, no
            // permission grant, no bytes read.
            hasAttachment = intent.hasExtra(Intent.EXTRA_STREAM),
            alreadyConsumed = intent.getBooleanExtra(EXTRA_CONSUMED, false)
        )
    }
}

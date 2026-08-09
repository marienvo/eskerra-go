package com.eskerra.go

import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.eskerra.go.core.share.PendingShare
import com.eskerra.go.core.share.ShareRead

/**
 * Turns an inbound share intent into the [PendingShare] the shell reads, and announces what could
 * not be used. Split out of `MainActivity` so the delivery rules stand on their own; [announce]
 * is a toast in the app.
 */
class ShareIntentConsumer(private val announce: (String) -> Unit) {

    /** Read inside setContent, so a share arriving while the app runs recomposes the shell. */
    val pending: MutableState<PendingShare?> = mutableStateOf(null)

    private var sequence = 0L

    fun consume(intent: Intent?) {
        val read = SharedIntentReader.read(intent)
        if (read is ShareRead.None) return
        // Belt and braces against any later re-read of the same intent, so a rotation cannot
        // prefill twice or repeat a toast.
        intent?.putExtra(SharedIntentReader.EXTRA_CONSUMED, true)
        when (read) {
            is ShareRead.Content -> {
                sequence += 1
                pending.value = PendingShare(sequence, read.content)
                if (read.ignoredAttachment) announce(IMAGE_IGNORED_MESSAGE)
            }
            ShareRead.AttachmentOnly -> announce(IMAGE_UNSUPPORTED_MESSAGE)
            ShareRead.None -> Unit
        }
    }

    /** Clears the share once the shell has applied it, ignoring acks for a newer one. */
    fun handled(id: Long) {
        if (pending.value?.id == id) {
            pending.value = null
        }
    }

    companion object {
        const val IMAGE_IGNORED_MESSAGE = "Image ignored"
        const val IMAGE_UNSUPPORTED_MESSAGE = "Images not supported yet"
    }
}

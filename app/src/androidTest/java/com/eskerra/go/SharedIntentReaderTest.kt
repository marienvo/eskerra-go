package com.eskerra.go

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eskerra.go.core.share.ShareRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because a real `Intent` cannot be built on the JVM here (no Robolectric). Every
 * decision beyond this boundary is covered by plain unit tests on `classifyShare`.
 */
@RunWith(AndroidJUnit4::class)
class SharedIntentReaderTest {

    @Test
    fun readsPlainTextShare() {
        val shared = contentOf(sendIntent(text = "https://example.com/a"))

        assertEquals("https://example.com/a", shared.content.text)
        assertNull(shared.content.subject)
        assertEquals(false, shared.ignoredAttachment)
    }

    @Test
    fun readsStyledTextShare() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, SpannableString("styled share"))
        }

        assertEquals("styled share", contentOf(intent).content.text)
    }

    @Test
    fun readsSubject() {
        val shared = contentOf(
            sendIntent(text = "https://example.com/a", subject = "A page title")
        )

        assertEquals("A page title", shared.content.subject)
    }

    @Test
    fun fallsBackToExtraTitle() {
        val intent = sendIntent(text = "https://example.com/a").apply {
            putExtra(Intent.EXTRA_TITLE, "From title")
        }

        assertEquals("From title", contentOf(intent).content.subject)
    }

    @Test
    fun ignoresMultipleShares() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "a")
        }

        assertEquals(ShareRead.None, SharedIntentReader.read(intent))
    }

    @Test
    fun readsAnImageShareForItsTextAndFlagsTheAttachment() {
        val intent = imageIntent().apply {
            putExtra(Intent.EXTRA_TEXT, "https://chatgpt.com/share/abc")
            putExtra(Intent.EXTRA_SUBJECT, "My thread")
        }

        val shared = contentOf(intent)

        assertEquals("https://chatgpt.com/share/abc", shared.content.text)
        assertEquals("My thread", shared.content.subject)
        assertTrue("an image alongside the text must be reported", shared.ignoredAttachment)
    }

    @Test
    fun reportsAnImageOnlyShare() {
        assertEquals(ShareRead.AttachmentOnly, SharedIntentReader.read(imageIntent()))
    }

    @Test
    fun ignoresUnsupportedPayloads() {
        val intent = sendIntent(text = "ignored").apply { type = "video/mp4" }

        assertEquals(ShareRead.None, SharedIntentReader.read(intent))
    }

    @Test
    fun ignoresOtherActions() {
        assertEquals(ShareRead.None, SharedIntentReader.read(Intent(Intent.ACTION_VIEW)))
        assertEquals(ShareRead.None, SharedIntentReader.read(null))
    }

    @Test
    fun ignoresBlankShare() {
        assertEquals(
            ShareRead.None,
            SharedIntentReader.read(sendIntent(text = "   ", subject = "  "))
        )
    }

    @Test
    fun ignoresAnAlreadyConsumedIntent() {
        val intent = sendIntent(text = "https://example.com/a").apply {
            putExtra(SharedIntentReader.EXTRA_CONSUMED, true)
        }

        assertEquals(ShareRead.None, SharedIntentReader.read(intent))
    }

    private fun contentOf(intent: Intent): ShareRead.Content {
        val read = SharedIntentReader.read(intent)
        assertTrue("expected content, got $read", read is ShareRead.Content)
        return read as ShareRead.Content
    }

    private fun sendIntent(text: String, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    private fun imageIntent() = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/thread.png"))
    }
}

package com.eskerra.go

import android.content.Intent
import android.text.SpannableString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented because a real `Intent` cannot be built on the JVM here (no Robolectric). Every
 * decision beyond this boundary is covered by plain unit tests on `SharedContent`.
 */
@RunWith(AndroidJUnit4::class)
class SharedIntentReaderTest {

    @Test
    fun readsPlainTextShare() {
        val shared = SharedIntentReader.read(sendIntent(text = "https://example.com/a"))

        assertEquals("https://example.com/a", shared?.text)
        assertNull(shared?.subject)
    }

    @Test
    fun readsStyledTextShare() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, SpannableString("styled share"))
        }

        assertEquals("styled share", SharedIntentReader.read(intent)?.text)
    }

    @Test
    fun readsSubject() {
        val shared = SharedIntentReader.read(
            sendIntent(text = "https://example.com/a", subject = "A page title")
        )

        assertEquals("A page title", shared?.subject)
    }

    @Test
    fun fallsBackToExtraTitle() {
        val intent = sendIntent(text = "https://example.com/a").apply {
            putExtra(Intent.EXTRA_TITLE, "From title")
        }

        assertEquals("From title", SharedIntentReader.read(intent)?.subject)
    }

    @Test
    fun ignoresMultipleShares() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "a")
        }

        assertNull(SharedIntentReader.read(intent))
    }

    @Test
    fun ignoresNonTextPayloads() {
        val intent = sendIntent(text = "ignored").apply { type = "image/png" }

        assertNull(SharedIntentReader.read(intent))
    }

    @Test
    fun ignoresOtherActions() {
        assertNull(SharedIntentReader.read(Intent(Intent.ACTION_VIEW)))
        assertNull(SharedIntentReader.read(null))
    }

    @Test
    fun ignoresBlankShare() {
        assertNull(SharedIntentReader.read(sendIntent(text = "   ", subject = "  ")))
    }

    @Test
    fun ignoresAnAlreadyConsumedIntent() {
        val intent = sendIntent(text = "https://example.com/a").apply {
            putExtra(SharedIntentReader.EXTRA_CONSUMED, true)
        }

        assertNull(SharedIntentReader.read(intent))
    }

    private fun sendIntent(text: String, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
}

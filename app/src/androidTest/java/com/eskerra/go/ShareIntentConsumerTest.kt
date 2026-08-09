package com.eskerra.go

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented for the same reason as [SharedIntentReaderTest]: a real `Intent`. */
@RunWith(AndroidJUnit4::class)
class ShareIntentConsumerTest {

    private val announced = mutableListOf<String>()
    private val consumer = ShareIntentConsumer { announced += it }

    @Test
    fun aTextSharePendsSilently() {
        consumer.consume(textIntent("https://example.com/a"))

        assertEquals("https://example.com/a", consumer.pending.value?.content?.text)
        assertTrue("a plain text share says nothing", announced.isEmpty())
    }

    @Test
    fun anImageWithTextPendsAndSaysTheImageWasDropped() {
        consumer.consume(imageIntent().apply { putExtra(Intent.EXTRA_TEXT, "https://a.example") })

        assertEquals("https://a.example", consumer.pending.value?.content?.text)
        assertEquals(listOf(ShareIntentConsumer.IMAGE_IGNORED_MESSAGE), announced)
    }

    @Test
    fun anImageOnlyShareOnlyAnnounces() {
        consumer.consume(imageIntent())

        assertNull(consumer.pending.value)
        assertEquals(listOf(ShareIntentConsumer.IMAGE_UNSUPPORTED_MESSAGE), announced)
    }

    @Test
    fun aRedeliveredIntentNeitherPendsAgainNorRepeatsTheToast() {
        val intent = imageIntent()

        consumer.consume(intent)
        consumer.consume(intent)

        assertEquals(listOf(ShareIntentConsumer.IMAGE_UNSUPPORTED_MESSAGE), announced)
    }

    @Test
    fun everyShareGetsAFreshId() {
        consumer.consume(textIntent("first"))
        val first = requireNotNull(consumer.pending.value).id
        consumer.consume(textIntent("second"))

        assertTrue("ids must advance", requireNotNull(consumer.pending.value).id > first)
    }

    @Test
    fun handledClearsOnlyTheShareThatWasApplied() {
        consumer.consume(textIntent("first"))
        val first = requireNotNull(consumer.pending.value).id
        consumer.consume(textIntent("second"))

        consumer.handled(first)
        assertEquals("second", consumer.pending.value?.content?.text)

        consumer.handled(requireNotNull(consumer.pending.value).id)
        assertNull(consumer.pending.value)
    }

    private fun textIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun imageIntent() = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/thread.png"))
    }
}

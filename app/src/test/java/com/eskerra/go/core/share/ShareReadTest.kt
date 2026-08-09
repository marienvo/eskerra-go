package com.eskerra.go.core.share

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The share classification, one step above the `Intent` boundary. An image share is accepted for
 * its text — never for its bytes — so the cases that matter are the combinations of type, text and
 * attachment.
 */
class ShareReadTest {

    @Test
    fun plainTextShareBecomesContent() {
        val read = classify(type = "text/plain", text = "https://example.com/a")

        assertEquals(
            ShareRead.Content(
                SharedContent("https://example.com/a", null),
                ignoredAttachment = false
            ),
            read
        )
    }

    @Test
    fun imageWithTextKeepsTheTextAndFlagsTheAttachment() {
        val read = classify(
            type = "image/png",
            text = "https://chatgpt.com/share/abc",
            subject = "My thread",
            hasAttachment = true
        )

        assertEquals(
            ShareRead.Content(
                SharedContent("https://chatgpt.com/share/abc", "My thread"),
                ignoredAttachment = true
            ),
            read
        )
    }

    @Test
    fun imageWithSubjectOnlyIsStillUsable() {
        val read = classify(type = "image/jpeg", subject = "My thread", hasAttachment = true)

        assertEquals(
            ShareRead.Content(SharedContent("", "My thread"), ignoredAttachment = true),
            read
        )
    }

    @Test
    fun imageWithoutAnyTextIsAttachmentOnly() {
        assertEquals(
            ShareRead.AttachmentOnly,
            classify(type = "image/png", hasAttachment = true)
        )
        assertEquals(
            ShareRead.AttachmentOnly,
            classify(type = "image/png", text = "  ", subject = " ", hasAttachment = true)
        )
    }

    @Test
    fun textShareWithAnAttachmentAlsoFlagsIt() {
        val read = classify(type = "text/plain", text = "a note", hasAttachment = true)

        assertEquals(
            ShareRead.Content(SharedContent("a note", null), ignoredAttachment = true),
            read
        )
    }

    @Test
    fun unsupportedTypesAreIgnored() {
        assertEquals(ShareRead.None, classify(type = "video/mp4", text = "a", hasAttachment = true))
        assertEquals(
            ShareRead.None,
            classify(type = "application/pdf", text = "a", hasAttachment = true)
        )
    }

    @Test
    fun aMissingTypeStillClassifiesOnItsExtras() {
        assertEquals(
            ShareRead.Content(SharedContent("a note", null), ignoredAttachment = false),
            classify(type = null, text = "a note")
        )
    }

    @Test
    fun otherActionsAreIgnored() {
        assertEquals(
            ShareRead.None,
            classify(isSendAction = false, type = "image/png", text = "a", hasAttachment = true)
        )
    }

    @Test
    fun anAlreadyConsumedShareIsIgnored() {
        assertEquals(
            ShareRead.None,
            classify(
                type = "image/png",
                text = "https://example.com/a",
                hasAttachment = true,
                alreadyConsumed = true
            )
        )
    }

    @Test
    fun anEmptyShareIsIgnored() {
        assertEquals(ShareRead.None, classify(type = "text/plain"))
        assertEquals(ShareRead.None, classify(type = "text/plain", text = "  ", subject = " "))
    }

    private fun classify(
        isSendAction: Boolean = true,
        type: String?,
        text: String? = null,
        subject: String? = null,
        hasAttachment: Boolean = false,
        alreadyConsumed: Boolean = false
    ) = classifyShare(
        isSendAction = isSendAction,
        type = type,
        text = text,
        subject = subject,
        hasAttachment = hasAttachment,
        alreadyConsumed = alreadyConsumed
    )
}

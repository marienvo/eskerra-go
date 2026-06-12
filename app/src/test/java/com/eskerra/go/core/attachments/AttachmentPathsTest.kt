package com.eskerra.go.core.attachments

import com.eskerra.go.core.model.NoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPathsTest {

    // ------- sanitizeAttachmentBaseName (attachmentPaths.test.ts) -------

    @Test
    fun sanitize_stripsPathsAndExtensionLowercasesReplacesSpaces() {
        assertEquals("photo-final", AttachmentPaths.sanitizeAttachmentBaseName("Photo FINAL.PNG"))
    }

    @Test
    fun sanitize_handlesNamesWithoutExtension() {
        assertEquals("screenshot", AttachmentPaths.sanitizeAttachmentBaseName("screenshot"))
    }

    @Test
    fun sanitize_fallsBackWhenEmptyAfterSanitize() {
        assertEquals("image", AttachmentPaths.sanitizeAttachmentBaseName("!!!"))
    }

    // ------- normalizeImageFileExtension -------

    @Test
    fun normalize_acceptsKnownExtensionsWithOrWithoutDot() {
        assertEquals(".png", AttachmentPaths.normalizeImageFileExtension("png"))
        assertEquals(".jpg", AttachmentPaths.normalizeImageFileExtension(".JPG"))
    }

    @Test
    fun normalize_rejectsUnknownExtensions() {
        assertNull(AttachmentPaths.normalizeImageFileExtension(".exe"))
    }

    // ------- buildAttachmentFileName -------

    @Test
    fun buildAttachmentFileName_buildsPredictableNameWithToken() {
        assertEquals(
            "shot-abc123.png",
            AttachmentPaths.buildAttachmentFileName("shot", ".png", "abc123")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildAttachmentFileName_throwsOnBadExtension() {
        AttachmentPaths.buildAttachmentFileName("a", ".exe", "1")
    }

    // ------- buildInboxRelativeAttachmentMarkdownPath -------

    @Test
    fun buildInboxRelativeAttachmentMarkdownPath_returnsStableRelativePath() {
        assertEquals("../Assets/Attachments", AttachmentPaths.inboxNoteRelativeAttachmentDir())
        assertEquals(
            "../Assets/Attachments/x.png",
            AttachmentPaths.buildInboxRelativeAttachmentMarkdownPath("x.png")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildInboxRelativeAttachmentMarkdownPath_rejectsPathSegmentsInFileName() {
        AttachmentPaths.buildInboxRelativeAttachmentMarkdownPath("a/b.png")
    }

    // ------- imageMimeToExtension -------

    @Test
    fun imageMimeToExtension_mapsCommonMimes() {
        assertEquals(".png", AttachmentPaths.imageMimeToExtension("image/png"))
        assertEquals(".jpg", AttachmentPaths.imageMimeToExtension("IMAGE/JPEG"))
        assertEquals(".svg", AttachmentPaths.imageMimeToExtension("image/svg+xml"))
    }

    @Test
    fun imageMimeToExtension_returnsNullForUnknown() {
        assertNull(AttachmentPaths.imageMimeToExtension("application/octet-stream"))
    }

    // ------- resolveVaultImageLoadTarget (resolveVaultImagePreviewUrl.test.ts) -------

    @Test
    fun resolve_passesThroughHttpAndDataUrls() {
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Remote("https://x/y.png"),
            AttachmentPaths.resolveVaultImageLoadTarget(
                NoteId("Inbox/n.md"),
                "https://x/y.png"
            )
        )
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Remote("data:image/png;base64,xx"),
            AttachmentPaths.resolveVaultImageLoadTarget(
                NoteId("Inbox/n.md"),
                "data:image/png;base64,xx"
            )
        )
    }

    @Test
    fun resolve_attachmentPathFromInboxNote() {
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Local("Assets/Attachments/a.png"),
            AttachmentPaths.resolveVaultImageLoadTarget(
                NoteId("Inbox/note.md"),
                "../Assets/Attachments/a.png"
            )
        )
    }

    @Test
    fun resolve_usesInboxAsBaseWhenSourceNoteIdIsNull() {
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Local("Assets/Attachments/a.png"),
            AttachmentPaths.resolveVaultImageLoadTarget(null, "../Assets/Attachments/a.png")
        )
    }

    @Test
    fun resolve_decodesPercentEncodingInPathBeforeResolving() {
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Local("Assets/Attachments/hello world.png"),
            AttachmentPaths.resolveVaultImageLoadTarget(
                NoteId("Inbox/note.md"),
                "../Assets/Attachments/hello%20world.png"
            )
        )
    }

    @Test
    fun resolve_nestedNoteDirectory() {
        assertEquals(
            AttachmentPaths.ResolvedImageSrc.Local("Assets/Attachments/photo.png"),
            AttachmentPaths.resolveVaultImageLoadTarget(
                NoteId("Notes/Projects/readme.md"),
                "../../Assets/Attachments/photo.png"
            )
        )
    }

    @Test
    fun resolve_rejectsNonImageExtensions() {
        val result = AttachmentPaths.resolveVaultImageLoadTarget(
            NoteId("Inbox/note.md"),
            "../Assets/Attachments/readme.md"
        )
        assertTrue(result is AttachmentPaths.ResolvedImageSrc.Unresolvable)
    }

    @Test
    fun hasAllowedImageExtension_matchesCaseInsensitively() {
        assertTrue(AttachmentPaths.hasAllowedImageExtension("Assets/x.PNG"))
        assertFalse(AttachmentPaths.hasAllowedImageExtension("Assets/x.pdf"))
    }

    @Test
    fun isSvgPath_detectsSvgExtension() {
        assertTrue(AttachmentPaths.isSvgPath("Assets/icon.svg"))
        assertFalse(AttachmentPaths.isSvgPath("Assets/icon.png"))
    }
}

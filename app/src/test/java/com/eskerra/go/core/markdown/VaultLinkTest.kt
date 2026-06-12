package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLinkTest {

    // ------- Test registry helpers -------

    private fun summary(path: String): NoteSummary = NoteSummary(
        id = NoteId(path),
        title = path.substringAfterLast('/').removeSuffix(".md"),
        snippet = "",
        isInbox = path.startsWith("Inbox/"),
        lastModifiedEpochMillis = 0L
    )

    private fun registry(vararg paths: String) = NoteRegistry(paths.map(::summary))

    // ------- stripMarkdownLinkHrefToPathPart -------

    @Test
    fun strip_removesQueryAndFragment() {
        assertEquals("./n.md", VaultLink.stripMarkdownLinkHrefToPathPart("./n.md?q=1#h"))
        assertEquals("./n.md", VaultLink.stripMarkdownLinkHrefToPathPart("./n.md#anchor"))
        assertEquals("./n.md", VaultLink.stripMarkdownLinkHrefToPathPart("./n.md?q=1"))
        assertEquals("./n.md", VaultLink.stripMarkdownLinkHrefToPathPart("  ./n.md  "))
    }

    // ------- isExternalMarkdownHref -------

    @Test
    fun isExternal_detectsSchemes() {
        assertTrue(VaultLink.isExternalMarkdownHref("https://a/b.md"))
        assertTrue(VaultLink.isExternalMarkdownHref("mailto:x"))
        assertTrue(VaultLink.isExternalMarkdownHref("//cdn/x.md"))
        assertTrue(VaultLink.isExternalMarkdownHref(""))
        assertFalse(VaultLink.isExternalMarkdownHref("./x.md"))
        assertFalse(VaultLink.isExternalMarkdownHref("../x.md"))
        assertFalse(VaultLink.isExternalMarkdownHref("Notes/x.md"))
    }

    @Test
    fun isExternal_eskerraWikiSchemeIsNotExternal() {
        // eskerra-wiki: contains `-` which is not a letter, so treated as internal
        assertFalse(VaultLink.isExternalMarkdownHref("eskerra-wiki:SomeNote"))
    }

    // ------- isBrowserOpenableMarkdownHref -------

    @Test
    fun browserOpenable_allowsHttpHttpsMailto() {
        assertTrue(VaultLink.isBrowserOpenableMarkdownHref("https://example.com/x"))
        assertTrue(VaultLink.isBrowserOpenableMarkdownHref("HTTP://example.com/"))
        assertTrue(VaultLink.isBrowserOpenableMarkdownHref("http://a"))
        assertTrue(VaultLink.isBrowserOpenableMarkdownHref("mailto:a@b"))
    }

    @Test
    fun browserOpenable_rejectsDangerousAndRelative() {
        assertFalse(VaultLink.isBrowserOpenableMarkdownHref("javascript:alert(1)"))
        assertFalse(VaultLink.isBrowserOpenableMarkdownHref("file:///etc/passwd"))
        assertFalse(VaultLink.isBrowserOpenableMarkdownHref("//cdn/x"))
        assertFalse(VaultLink.isBrowserOpenableMarkdownHref("./note.md"))
        assertFalse(VaultLink.isBrowserOpenableMarkdownHref(""))
    }

    // ------- posixResolveRelativeToDirectory -------

    @Test
    fun posix_resolvesDotDotFromSubdirectory() {
        assertEquals(
            "Inbox/Beta.md",
            VaultLink.posixResolveRelativeToDirectory("Inbox/sub", "../Beta.md")
        )
    }

    @Test
    fun posix_resolvesDotInSameDirectory() {
        assertEquals(
            "Inbox/sub/gamma.md",
            VaultLink.posixResolveRelativeToDirectory("Inbox/sub", "./gamma.md")
        )
    }

    @Test
    fun posix_decodesPercentEncoding() {
        assertEquals(
            "Inbox/my note.md",
            VaultLink.posixResolveRelativeToDirectory("Inbox", "my%20note.md")
        )
    }

    @Test
    fun posix_clampsAtRoot() {
        // Excessive `..` stays at root, does not go negative
        assertEquals(
            "Other/note.md",
            VaultLink.posixResolveRelativeToDirectory("Inbox", "../../../../Other/note.md")
        )
    }

    @Test
    fun posix_barePathRelativeToSourceDir() {
        assertEquals(
            "Inbox/sub/Other.md",
            VaultLink.posixResolveRelativeToDirectory("Inbox/sub", "Other.md")
        )
    }

    // ------- resolveVaultRelativeMarkdownHref -------

    @Test
    fun resolve_sameDirLink() {
        val reg = registry("Inbox/alpha.md", "Inbox/Beta.md")
        val result = VaultLink.resolveVaultRelativeMarkdownHref(
            NoteId("Inbox/alpha.md"),
            "./Beta.md",
            reg
        )
        assertEquals(NoteId("Inbox/Beta.md"), result)
    }

    @Test
    fun resolve_parentDirLink() {
        val reg = registry("Inbox/Beta.md", "Inbox/sub/gamma.md")
        val result = VaultLink.resolveVaultRelativeMarkdownHref(
            NoteId("Inbox/sub/gamma.md"),
            "../Beta.md",
            reg
        )
        assertEquals(NoteId("Inbox/Beta.md"), result)
    }

    @Test
    fun resolve_caseInsensitiveRegistryLookup() {
        val reg = registry("Inbox/Beta.md")
        // href uses uppercase file name matching a lowercase registry entry
        val result = VaultLink.resolveVaultRelativeMarkdownHref(
            NoteId("Inbox/alpha.md"),
            "./BETA.MD",
            reg
        )
        assertEquals(NoteId("Inbox/Beta.md"), result)
    }

    @Test
    fun resolve_notMdExtension_returnsNull() {
        val reg = registry("Inbox/image.png")
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "./image.png",
                reg
            )
        )
    }

    @Test
    fun resolve_externalHref_returnsNull() {
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "https://example.com/note.md",
                registry()
            )
        )
    }

    @Test
    fun resolve_absolutePath_returnsNull() {
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "/absolute/note.md",
                registry()
            )
        )
    }

    @Test
    fun resolve_excludedDirAssets_returnsNull() {
        val reg = registry("Assets/hidden.md")
        // `Assets` is an excluded directory segment; must not be resolvable
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "../Assets/hidden.md",
                reg
            )
        )
    }

    @Test
    fun resolve_dotPrefixedDirIsExcluded() {
        val reg = registry(".hidden/note.md")
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "../.hidden/note.md",
                reg
            )
        )
    }

    @Test
    fun resolve_targetNotInRegistry_returnsNull() {
        // The resolved path is valid but no note with that path exists
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "./ghost.md",
                registry()
            )
        )
    }

    @Test
    fun resolve_deepEscapeAttemptClampsToRoot() {
        // Even an extreme `..` sequence ends up at vault root, not outside it.
        // As long as no matching note exists it returns null.
        assertNull(
            VaultLink.resolveVaultRelativeMarkdownHref(
                NoteId("Inbox/a.md"),
                "../../../../etc/passwd.md",
                registry()
            )
        )
    }

    @Test
    fun resolve_queryAndFragmentAreStripped() {
        val reg = registry("Inbox/Beta.md")
        val result = VaultLink.resolveVaultRelativeMarkdownHref(
            NoteId("Inbox/alpha.md"),
            "./Beta.md?section=1#heading",
            reg
        )
        assertEquals(NoteId("Inbox/Beta.md"), result)
    }

    @Test
    fun resolve_sourceAtVaultRoot() {
        val reg = registry("Other/note.md")
        val result = VaultLink.resolveVaultRelativeMarkdownHref(
            NoteId("index.md"),
            "Other/note.md",
            reg
        )
        assertEquals(NoteId("Other/note.md"), result)
    }
}

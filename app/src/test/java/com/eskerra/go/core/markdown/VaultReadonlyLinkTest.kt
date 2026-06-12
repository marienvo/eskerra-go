package com.eskerra.go.core.markdown

import com.eskerra.go.core.markdown.VaultReadonlyLink.IndexStatus
import com.eskerra.go.core.markdown.VaultReadonlyLink.LinkTarget
import com.eskerra.go.core.markdown.VaultReadonlyLink.LinkTone
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultReadonlyLinkTest {

    private fun summary(path: String, title: String): NoteSummary = NoteSummary(
        id = NoteId(path),
        title = title,
        snippet = "",
        isInbox = path.startsWith("Inbox/"),
        lastModifiedEpochMillis = 0L
    )

    private fun registry(vararg notes: NoteSummary) = NoteRegistry(notes.toList())

    private fun wikiHref(inner: String): String =
        VaultMarkdownPreprocess.wikiLinksToSyntheticMarkdownLinks("[[$inner]]")
            .substringAfter("](").substringBeforeLast(")")

    @Test
    fun decode_roundTripsWikiHref() {
        assertEquals("Alpha|Beta", VaultReadonlyLink.decodeWikiHref(wikiHref("Alpha|Beta")))
        assertEquals(null, VaultReadonlyLink.decodeWikiHref("https://example.com"))
    }

    @Test
    fun external_detectsSchemes() {
        assert(VaultReadonlyLink.isExternalHref("https://x.com"))
        assert(VaultReadonlyLink.isExternalHref("HTTP://x.com"))
        assert(VaultReadonlyLink.isExternalHref("mailto:a@b.com"))
        assert(!VaultReadonlyLink.isExternalHref("../Other.md"))
    }

    @Test
    fun tone_resolvedWikiIsInternal() {
        val reg = registry(summary("Notes/Alpha.md", "Alpha"))
        assertEquals(
            LinkTone.INTERNAL,
            VaultReadonlyLink.toneFor(wikiHref("Alpha"), reg, IndexStatus.READY)
        )
    }

    @Test
    fun tone_externalWikiInnerIsExternal() {
        val reg = registry()
        assertEquals(
            LinkTone.EXTERNAL,
            VaultReadonlyLink.toneFor(wikiHref("https://example.com"), reg, IndexStatus.READY)
        )
    }

    @Test
    fun tone_unresolvedWikiIsOptimisticWhileLoadingMutedWhenReady() {
        val reg = registry()
        val href = wikiHref("Ghost")
        assertEquals(LinkTone.INTERNAL, VaultReadonlyLink.toneFor(href, reg, IndexStatus.LOADING))
        assertEquals(LinkTone.MUTED, VaultReadonlyLink.toneFor(href, reg, IndexStatus.READY))
    }

    @Test
    fun tone_browserLinkIsExternal() {
        assertEquals(
            LinkTone.EXTERNAL,
            VaultReadonlyLink.toneFor("https://example.com", registry(), IndexStatus.READY)
        )
    }

    @Test
    fun target_resolvedWikiReturnsInternalNote() {
        val reg = registry(summary("Notes/Alpha.md", "Alpha"))
        assertEquals(
            LinkTarget.Internal(NoteId("Notes/Alpha.md")),
            VaultReadonlyLink.targetFor(wikiHref("Alpha"), reg)
        )
    }

    @Test
    fun target_ambiguousWikiReturnsCandidates() {
        val reg = registry(summary("A/Alpha.md", "Alpha"), summary("B/Alpha.md", "Alpha"))
        val target = VaultReadonlyLink.targetFor(wikiHref("Alpha"), reg)
        assert(target is LinkTarget.Ambiguous)
        assertEquals(2, (target as LinkTarget.Ambiguous).candidates.size)
    }

    @Test
    fun target_externalReturnsUrl() {
        assertEquals(
            LinkTarget.External("https://example.com"),
            VaultReadonlyLink.targetFor("https://example.com", registry())
        )
    }

    @Test
    fun target_unresolvedWikiReturnsUnresolved() {
        assertEquals(
            LinkTarget.Unresolved,
            VaultReadonlyLink.targetFor(wikiHref("Ghost"), registry())
        )
    }

    // ------- Phase 4: relative .md link resolution -------

    private fun reg(vararg paths: String) = NoteRegistry(
        paths.map { p ->
            NoteSummary(
                id = NoteId(p),
                title = p.substringAfterLast('/').removeSuffix(".md"),
                snippet = "",
                isInbox = p.startsWith("Inbox/"),
                lastModifiedEpochMillis = 0L
            )
        }
    )

    @Test
    fun tone_relativeMdLinkResolved_isInternal() {
        val r = reg("Inbox/Beta.md")
        val src = NoteId("Inbox/alpha.md")
        assertEquals(
            LinkTone.INTERNAL,
            VaultReadonlyLink.toneFor("./Beta.md", r, IndexStatus.READY, src)
        )
    }

    @Test
    fun tone_relativeMdLinkUnresolved_isMutedWhenReady() {
        val r = reg()
        val src = NoteId("Inbox/alpha.md")
        assertEquals(
            LinkTone.MUTED,
            VaultReadonlyLink.toneFor("./ghost.md", r, IndexStatus.READY, src)
        )
    }

    @Test
    fun tone_relativeMdLinkUnresolved_isOptimisticWhileLoading() {
        val r = reg()
        val src = NoteId("Inbox/alpha.md")
        assertEquals(
            LinkTone.INTERNAL,
            VaultReadonlyLink.toneFor("./ghost.md", r, IndexStatus.LOADING, src)
        )
    }

    @Test
    fun target_relativeMdLinkResolved_returnsInternal() {
        val r = reg("Inbox/Beta.md")
        val src = NoteId("Inbox/alpha.md")
        assertEquals(
            LinkTarget.Internal(NoteId("Inbox/Beta.md")),
            VaultReadonlyLink.targetFor("./Beta.md", r, src)
        )
    }

    @Test
    fun target_relativeMdLinkUnresolved_returnsUnresolved() {
        val r = reg()
        val src = NoteId("Inbox/alpha.md")
        assertEquals(
            LinkTarget.Unresolved,
            VaultReadonlyLink.targetFor("./ghost.md", r, src)
        )
    }

    @Test
    fun target_relativeMdParentDir_navigatesCorrectly() {
        val r = reg("Inbox/Beta.md", "Inbox/sub/gamma.md")
        val src = NoteId("Inbox/sub/gamma.md")
        assertEquals(
            LinkTarget.Internal(NoteId("Inbox/Beta.md")),
            VaultReadonlyLink.targetFor("../Beta.md", r, src)
        )
    }
}

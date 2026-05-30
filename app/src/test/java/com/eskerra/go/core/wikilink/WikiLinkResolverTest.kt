package com.eskerra.go.core.wikilink

import com.eskerra.go.core.model.AmbiguousWikiLink
import com.eskerra.go.core.model.MissingWikiLink
import com.eskerra.go.core.model.MissingWikiLinkReason
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import com.eskerra.go.core.model.ResolvedWikiLink
import com.eskerra.go.core.model.WikiLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinkResolverTest {

    @Test
    fun resolve_exactRelativePath_returnsResolved() {
        val registry = registryOf(note("Inbox/hello.md", "Hello"))
        val link = WikiLink("Inbox/hello.md", "Inbox/hello.md", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_pathWithoutExtension_returnsResolved() {
        val registry = registryOf(note("Inbox/hello.md", "Hello"))
        val link = WikiLink("Inbox/hello", "Inbox/hello", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_mdTarget_returnsResolved() {
        val registry = registryOf(note("Inbox/hello.md", "Hello"))
        val link = WikiLink("Inbox/hello.md", "Inbox/hello.md", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
    }

    @Test
    fun resolve_markdownTarget_returnsResolved() {
        val registry = registryOf(note("Inbox/hello.markdown", "Hello"))
        val link = WikiLink("Inbox/hello.markdown", "Inbox/hello.markdown", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.markdown", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_pathWithoutExtension_matchesUppercaseMarkdownExtension() {
        val registry = registryOf(note("Inbox/hello.MD", "Hello"))
        val link = WikiLink("Inbox/hello", "Inbox/hello", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.MD", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_mdTargetAgainstMarkdownOnly_usesExtensionlessFallback() {
        val registry = registryOf(note("Inbox/hello.markdown", "Hello"))
        val link = WikiLink("Inbox/hello.md", "Inbox/hello.md", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.markdown", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_byTitle_returnsResolved() {
        val registry = registryOf(note("Notes/plan.md", "Project Plan"))
        val link = WikiLink("Project Plan", "Project Plan", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Notes/plan.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_byFilenameStem_returnsResolved() {
        val registry = registryOf(note("Notes/project-plan.md", "Project Plan"))
        val link = WikiLink("project-plan", "project-plan", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Notes/project-plan.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_byFilenameStem_matchesUppercaseMarkdownExtension() {
        val registry = registryOf(note("Notes/project-plan.MARKDOWN", "Project Plan"))
        val link = WikiLink("project-plan", "project-plan", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Notes/project-plan.MARKDOWN", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_missingTarget_returnsMissing() {
        val registry = registryOf(note("Notes/existing.md", "Existing"))
        val link = WikiLink("Does Not Exist", "Does Not Exist", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is MissingWikiLink)
        assertEquals(MissingWikiLinkReason.NoMatch, (resolution as MissingWikiLink).reason)
    }

    @Test
    fun resolve_duplicateTitle_returnsAmbiguous() {
        val registry =
            registryOf(
                note("Inbox/daily-a.md", "Daily"),
                note("Archive/daily-b.md", "Daily")
            )
        val link = WikiLink("Daily", "Daily", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is AmbiguousWikiLink)
        val candidates = (resolution as AmbiguousWikiLink).candidates
        assertEquals(
            listOf("Archive/daily-b.md", "Inbox/daily-a.md"),
            candidates.map {
                it.id.value
            }
        )
    }

    @Test
    fun resolve_duplicateStem_returnsAmbiguous() {
        val registry =
            registryOf(
                note("Inbox/daily.md", "Morning"),
                note("Archive/daily.md", "Evening")
            )
        val link = WikiLink("daily", "daily", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is AmbiguousWikiLink)
        val candidates = (resolution as AmbiguousWikiLink).candidates
        assertEquals(listOf("Archive/daily.md", "Inbox/daily.md"), candidates.map { it.id.value })
    }

    @Test
    fun resolve_pathTraversal_returnsMissingWithTraversalReason() {
        val registry = registryOf(note("secret/hidden.md", "Hidden"))
        val link = WikiLink("../secret", "../secret", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is MissingWikiLink)
        assertEquals(MissingWikiLinkReason.PathTraversal, (resolution as MissingWikiLink).reason)
    }

    @Test
    fun resolve_nestedFolderPath_returnsResolved() {
        val registry = registryOf(note("Projects/App/Plan.md", "Plan"))
        val link = WikiLink("Projects/App/Plan", "Projects/App/Plan", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Projects/App/Plan.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    @Test
    fun resolve_candidatesAreDeterministicallySorted() {
        val registry =
            NoteRegistry.fromNotes(
                listOf(
                    note("Inbox/z-last.md", "Daily"),
                    note("Archive/a-first.md", "Daily")
                )
            )
        val link = WikiLink("Daily", "Daily", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is AmbiguousWikiLink)
        assertEquals(
            listOf("Archive/a-first.md", "Inbox/z-last.md"),
            (resolution as AmbiguousWikiLink).candidates.map { it.id.value }
        )
    }

    @Test
    fun resolve_emptyTarget_returnsMissingWithEmptyReason() {
        val registry = registryOf(note("Inbox/note.md", "Note"))
        val link = WikiLink("", "Label", 0..0, false)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is MissingWikiLink)
        assertEquals(MissingWikiLinkReason.EmptyTarget, (resolution as MissingWikiLink).reason)
    }

    @Test
    fun resolveAll_preservesInputOrder() {
        val registry =
            registryOf(
                note("Inbox/first.md", "First"),
                note("Inbox/second.md", "Second")
            )
        val links =
            listOf(
                WikiLink("First", "First", 0..0, true),
                WikiLink("Missing", "Missing", 0..0, true),
                WikiLink("Second", "Second", 0..0, true)
            )

        val resolutions = WikiLinkResolver.resolveAll(links, registry)

        assertTrue(resolutions[0] is ResolvedWikiLink)
        assertTrue(resolutions[1] is MissingWikiLink)
        assertTrue(resolutions[2] is ResolvedWikiLink)
    }

    @Test
    fun resolve_absoluteLikePath_returnsMissingWithTraversalReason() {
        val registry = registryOf(note("Inbox/note.md", "Note"))
        val link = WikiLink("/Inbox/note.md", "/Inbox/note.md", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is MissingWikiLink)
        assertEquals(MissingWikiLinkReason.PathTraversal, (resolution as MissingWikiLink).reason)
    }

    @Test
    fun resolve_backslashPath_normalizesToForwardSlashes() {
        val registry = registryOf(note("Inbox/hello.md", "Hello"))
        val link = WikiLink("Inbox\\hello.md", "Hello", 0..0, true)

        val resolution = WikiLinkResolver.resolve(link, registry)

        assertTrue(resolution is ResolvedWikiLink)
        assertEquals("Inbox/hello.md", (resolution as ResolvedWikiLink).note.id.value)
    }

    private fun note(path: String, title: String): NoteSummary = NoteSummary(
        id = NoteId(path),
        title = title,
        snippet = "",
        isInbox = path.startsWith("Inbox/")
    )

    private fun registryOf(vararg notes: NoteSummary): NoteRegistry =
        NoteRegistry.fromNotes(notes.toList())
}

package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedMarkdownLinksTest {

    private fun note(
        path: String,
        title: String = path.substringAfterLast('/').removeSuffix(".md")
    ) = NoteSummary(
        id = NoteId(path),
        title = title,
        snippet = "",
        isInbox = path.startsWith("Inbox/")
    )

    private fun registryOf(vararg notes: NoteSummary) = NoteRegistry.fromNotes(notes.toList())

    @Test
    fun resolvesUnambiguousWikiLink() = runTest {
        val source = note("Inbox/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)
        val prepared = prepareVaultMarkdown("See [[Target]] for details.")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(target.id), result.targets.map { it.noteId })
    }

    @Test
    fun resolvesInlineMarkdownLink_withAngleBracketDestination() = runTest {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/My Target.md", "My Target")
        val registry = registryOf(source, target)
        // The angle-bracket form is what a filename containing spaces normally uses, and is a case
        // the previous regex-based prefetch scan did not special-case.
        val prepared = prepareVaultMarkdown("Jump to [it](<./My Target.md>).")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(target.id), result.targets.map { it.noteId })
    }

    @Test
    fun resolvesInlineMarkdownLink_withTitleSuffix() = runTest {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Sibling.md", "Sibling")
        val registry = registryOf(source, target)
        val prepared = prepareVaultMarkdown("Jump to [sibling](./Sibling.md \"Sibling note\").")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(target.id), result.targets.map { it.noteId })
    }

    @Test
    fun skipsAmbiguousWikiLink() = runTest {
        val source = note("Inbox/Source.md", "Source")
        val a = note("A/Dup.md", "Dup")
        val b = note("B/Dup.md", "Dup")
        val registry = registryOf(source, a, b)
        val prepared = prepareVaultMarkdown("Ambiguous [[Dup]] link.")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertTrue(result.targets.isEmpty())
    }

    @Test
    fun skipsExternalAndUnresolvableLinks() = runTest {
        val source = note("Notes/Source.md", "Source")
        val registry = registryOf(source)
        val prepared = prepareVaultMarkdown("[web](https://example.com) and [gone](./Nowhere.md)")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertTrue(result.targets.isEmpty())
    }

    @Test
    fun deduplicatesAndExcludesSourceNote() = runTest {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)
        val prepared = prepareVaultMarkdown(
            "[[Target]] again [[Target]] and self [[Source]] and [t](./Target.md)"
        )

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(target.id), result.targets.map { it.noteId })
    }

    @Test
    fun resolvesLinksInsideCalloutBody() = runTest {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)
        val prepared = prepareVaultMarkdown("> [!note] Heads up\n> See [[Target]].")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(target.id), result.targets.map { it.noteId })
    }

    @Test
    fun preservesFirstSeenOrder() = runTest {
        val source = note("Notes/Source.md", "Source")
        val first = note("Notes/First.md", "First")
        val second = note("Notes/Second.md", "Second")
        val registry = registryOf(source, first, second)
        val prepared = prepareVaultMarkdown("[[Second]] then [[First]]")

        val result = PreparedMarkdownLinks.resolve(prepared, source.id, registry)

        assertEquals(listOf(second.id, first.id), result.targets.map { it.noteId })
    }
}

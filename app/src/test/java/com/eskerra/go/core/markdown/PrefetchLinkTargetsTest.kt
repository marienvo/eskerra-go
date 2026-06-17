package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefetchLinkTargetsTest {

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
    fun resolvesUnambiguousWikiLinkByTitle() {
        val source = note("Inbox/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)

        val result = PrefetchLinkTargets.resolve(
            markdown = "See [[Target]] for details.",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun skipsAmbiguousWikiLink() {
        val source = note("Inbox/Source.md", "Source")
        val a = note("A/Dup.md", "Dup")
        val b = note("B/Dup.md", "Dup")
        val registry = registryOf(source, a, b)

        val result = PrefetchLinkTargets.resolve(
            markdown = "Ambiguous [[Dup]] link.",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(emptyList<NoteId>(), result)
    }

    @Test
    fun skipsMissingWikiLink() {
        val source = note("Inbox/Source.md", "Source")
        val registry = registryOf(source)

        val result = PrefetchLinkTargets.resolve(
            markdown = "Dangling [[Nowhere]].",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(emptyList<NoteId>(), result)
    }

    @Test
    fun resolvesRelativeMarkdownLink() {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Sibling.md", "Sibling")
        val registry = registryOf(source, target)

        val result = PrefetchLinkTargets.resolve(
            markdown = "Jump to [sibling](./Sibling.md).",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun ignoresExternalAndNonMarkdownInlineLinks() {
        val source = note("Notes/Source.md", "Source")
        val registry = registryOf(source)

        val result = PrefetchLinkTargets.resolve(
            markdown = "[web](https://example.com) and [img](./pic.png)",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(emptyList<NoteId>(), result)
    }

    @Test
    fun deduplicatesAndExcludesSourceNote() {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)

        val result = PrefetchLinkTargets.resolve(
            markdown = "[[Target]] again [[Target]] and self [[Source]] and [t](./Target.md)",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(listOf(target.id), result)
    }

    @Test
    fun preservesFirstSeenOrder() {
        val source = note("Notes/Source.md", "Source")
        val first = note("Notes/First.md", "First")
        val second = note("Notes/Second.md", "Second")
        val registry = registryOf(source, first, second)

        val result = PrefetchLinkTargets.resolve(
            markdown = "[[Second]] then [[First]]",
            sourceNoteId = source.id,
            registry = registry
        )

        assertEquals(listOf(second.id, first.id), result)
    }
}

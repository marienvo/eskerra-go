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

    @Test
    fun resolveWithOffsets_reportsFirstSeenLinkStartOffset() {
        val source = note("Notes/Source.md", "Source")
        val target = note("Notes/Target.md", "Target")
        val registry = registryOf(source, target)
        val markdown = "Padding. [[Target]] and again [[Target]]."

        val result = PrefetchLinkTargets.resolveWithOffsets(markdown, source.id, registry)

        assertEquals(1, result.size)
        assertEquals(target.id, result[0].noteId)
        assertEquals(markdown.indexOf("[[Target]]"), result[0].sourceOffset)
    }

    @Test
    fun orderByViewport_prioritizesTargetInsideVisibleWindow() {
        val near = PrefetchLinkTargets.Target(NoteId("Near.md"), sourceOffset = 500)
        val far = PrefetchLinkTargets.Target(NoteId("Far.md"), sourceOffset = 10)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(far, near),
            markdownLength = 1000,
            visibleStartFraction = 0.45f,
            visibleEndFraction = 0.55f
        )

        assertEquals(listOf(NoteId("Near.md"), NoteId("Far.md")), result)
    }

    @Test
    fun orderByViewport_ordersByDistanceOutsideWindow() {
        val closest = PrefetchLinkTargets.Target(NoteId("Closest.md"), sourceOffset = 600)
        val furthest = PrefetchLinkTargets.Target(NoteId("Furthest.md"), sourceOffset = 900)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(furthest, closest),
            markdownLength = 1000,
            visibleStartFraction = 0f,
            visibleEndFraction = 0.1f
        )

        assertEquals(listOf(NoteId("Closest.md"), NoteId("Furthest.md")), result)
    }

    @Test
    fun orderByViewport_fallsBackToIncomingOrder_whenLengthNotPositive() {
        val a = PrefetchLinkTargets.Target(NoteId("A.md"), sourceOffset = 5)
        val b = PrefetchLinkTargets.Target(NoteId("B.md"), sourceOffset = 1)

        val result = PrefetchLinkTargets.orderByViewport(
            targets = listOf(a, b),
            markdownLength = 0,
            visibleStartFraction = 0f,
            visibleEndFraction = 1f
        )

        assertEquals(listOf(NoteId("A.md"), NoteId("B.md")), result)
    }
}

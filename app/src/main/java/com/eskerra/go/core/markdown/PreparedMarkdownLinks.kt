package com.eskerra.go.core.markdown

import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.mikepenz.markdown.model.State
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

/**
 * Collects the notes a rendered [PreparedMarkdown] body actually links to — walking the *parsed*
 * AST and resolving each `INLINE_LINK` destination with [VaultReadonlyLink.targetFor], the exact
 * function [com.eskerra.go.ui.markdown.VaultMarkdownAnnotator] uses to route a tap. This makes
 * prefetch tappable-link-for-tappable-link identical to the reader, which the previous regex-based
 * scan (`PrefetchLinkTargets.resolve`, now removed) was not: it could miss an href the AST parser
 * accepts (a `<...>`-wrapped destination, one with a trailing title, etc.) and therefore never
 * prefetch a link the user could actually tap.
 *
 * Both `[[wiki links]]` and inline `[label](href)` links appear as the same `INLINE_LINK` node
 * here — wiki links are already rewritten to synthetic `eskerra-wiki:` markdown links by
 * [VaultMarkdownPreprocess.preprocessVaultReadonlyMarkdownBody] before parsing — so one walk
 * covers both, and `targetFor` decodes the synthetic scheme itself.
 *
 * Ambiguous links and the source note itself are excluded, same as before: there is no single file
 * to warm for an ambiguous link, and warming the note you're already reading is pointless.
 */
object PreparedMarkdownLinks {

    /** [targets] in first-seen order, plus the synthesized total length [orderByViewport] scales against. */
    data class Result(val targets: List<PrefetchLinkTargets.Target>, val totalLength: Int)

    fun resolve(prepared: PreparedMarkdown, sourceNoteId: NoteId, registry: NoteRegistry): Result {
        val offsetByNoteId = LinkedHashMap<NoteId, Int>()
        var cursor = 0
        for (segment in prepared.segments) {
            cursor = when (segment) {
                is PreparedSegment.Markdown ->
                    collectLinks(segment.state, cursor, sourceNoteId, registry, offsetByNoteId)
                is PreparedSegment.Callout -> {
                    val body = segment.body
                    if (body != null) {
                        collectLinks(body, cursor, sourceNoteId, registry, offsetByNoteId)
                    } else {
                        cursor
                    }
                }
            }
        }
        offsetByNoteId.remove(sourceNoteId)
        val targets = offsetByNoteId.map { (noteId, offset) ->
            PrefetchLinkTargets.Target(noteId, offset)
        }
        return Result(targets, cursor)
    }

    /** Walks one parsed run's AST, resolving every internal link into [into]; returns the next cursor. */
    private fun collectLinks(
        state: State,
        cursorStart: Int,
        sourceNoteId: NoteId,
        registry: NoteRegistry,
        into: MutableMap<NoteId, Int>
    ): Int {
        val success = state as? State.Success ?: return cursorStart
        val content = success.content
        walk(success.node) { node ->
            if (node.type != MarkdownElementTypes.INLINE_LINK) return@walk
            val destNode = node.children.firstOrNull {
                it.type == MarkdownElementTypes.LINK_DESTINATION
            } ?: return@walk
            val href = content.substring(destNode.startOffset, destNode.endOffset)
                .trim()
                .removeSurrounding("<", ">")
            val target = VaultReadonlyLink.targetFor(href, registry, sourceNoteId)
            if (target is VaultReadonlyLink.LinkTarget.Internal) {
                into.putIfAbsent(target.noteId, cursorStart + node.startOffset)
            }
        }
        return cursorStart + content.length
    }

    private fun walk(node: ASTNode, visit: (ASTNode) -> Unit) {
        visit(node)
        node.children.forEach { walk(it, visit) }
    }
}

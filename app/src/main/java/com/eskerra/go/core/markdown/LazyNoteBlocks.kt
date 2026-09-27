package com.eskerra.go.core.markdown

import com.mikepenz.markdown.model.State
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

/**
 * Splits a [PreparedMarkdown] into small, independently renderable [PreparedSegment]s so the note
 * reader's `LazyColumn` only composes what's on (or near) screen, instead of the whole note at
 * once — the dominant cost for a long note (specs/performance/note-switching-logbook.md). A 50+
 * item bullet checklist that used to compose as one giant unit now composes in chunks of
 * [LIST_ITEM_CHUNK_SIZE].
 *
 * Every resulting segment still renders through the exact same
 * [com.mikepenz.markdown.compose.Markdown]`(state, colors, typography, annotator, components,
 * modifier)` call the non-lazy renderer already uses — the one already proven correct throughout
 * this app (see [com.eskerra.go.ui.markdown.VaultMarkdownSegmentContent], shared by both). No new
 * `CompositionLocal` wiring is invented here: that call provides those internally from the
 * `colors` / `typography` / `annotator` / `components` parameters it is already called with, and
 * several of them (`MarkdownColors`, `MarkdownTypography`, …) throw if a caller *doesn't* go
 * through that path — which is exactly why this splits data (a smaller [ASTNode] tree fed into the
 * same call) rather than trying to reimplement per-node rendering.
 *
 * Splitting a document's *top-level* blocks (paragraph, heading, list, table, code block, block
 * quote, …) apart is safe because each is fully self-contained: a list keeps its own item markers
 * internally, a paragraph needs no sibling context. Every synthesized [State.Success] reuses the
 * *same* already-parsed `content` string and `referenceLinkHandler` (computed once, over the whole
 * original text), so reference-style link definitions keep resolving correctly; only which subset
 * of nodes gets handed to the renderer changes. A large **unordered** list is additionally chunked
 * into groups of [LIST_ITEM_CHUNK_SIZE] items — safe because a bullet marker never depends on the
 * item's position — which is what actually shrinks a long checklist's first-frame cost. Ordered
 * lists are deliberately left whole: a numbered marker can depend on position within the list, and
 * this repo has no device to verify a chunked numbering scheme renders correctly.
 */
object LazyNoteBlocks {

    const val LIST_ITEM_CHUNK_SIZE = 10

    /** A lazy render item and its source range in [PreparedMarkdownLinks]' coordinate space. */
    data class Block(
        val segment: PreparedSegment,
        val sourceStartOffset: Int,
        val sourceEndOffset: Int
    )

    fun split(prepared: PreparedMarkdown): List<PreparedSegment> =
        splitWithSourceRanges(prepared).map { it.segment }

    fun splitWithSourceRanges(prepared: PreparedMarkdown): List<Block> {
        val blocks = mutableListOf<Block>()
        var cursor = 0
        for (segment in prepared.segments) {
            when (segment) {
                is PreparedSegment.Markdown -> {
                    splitMarkdownRun(segment.state, cursor, blocks)
                    cursor += contentLength(segment.state)
                }

                is PreparedSegment.Callout -> {
                    val length = segment.body?.let(::contentLength) ?: 0
                    blocks += Block(segment, cursor, cursor + length)
                    cursor += length
                }
            }
        }
        return blocks
    }

    private fun splitMarkdownRun(state: State, cursor: Int, into: MutableList<Block>) {
        val success = state as? State.Success
        val topLevel = success?.node?.children?.filter { isRenderableTopLevel(it.type) }
        if (success == null || topLevel.isNullOrEmpty()) {
            into += Block(PreparedSegment.Markdown(state), cursor, cursor + contentLength(state))
            return
        }
        for (child in topLevel) {
            val items = child.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
            if (child.type == MarkdownElementTypes.UNORDERED_LIST &&
                items.size > LIST_ITEM_CHUNK_SIZE
            ) {
                items.chunked(LIST_ITEM_CHUNK_SIZE).forEach { chunk ->
                    val innerList = GroupedNode(MarkdownElementTypes.UNORDERED_LIST, chunk)
                    into += Block(
                        segment = PreparedSegment.Markdown(regroup(success, listOf(innerList))),
                        sourceStartOffset = cursor + chunk.first().startOffset,
                        sourceEndOffset = cursor + chunk.last().endOffset
                    )
                }
            } else {
                into += Block(
                    segment = PreparedSegment.Markdown(regroup(success, listOf(child))),
                    sourceStartOffset = cursor + child.startOffset,
                    sourceEndOffset = cursor + child.endOffset
                )
            }
        }
    }

    private fun contentLength(state: State): Int = (state as? State.Success)?.content?.length ?: 0

    private fun isRenderableTopLevel(type: IElementType): Boolean =
        type != MarkdownTokenTypes.EOL &&
            type != MarkdownTokenTypes.WHITE_SPACE &&
            type != MarkdownElementTypes.LINK_DEFINITION

    /** Wraps [members] as the top-level children of a synthetic file-like root over [success]'s content. */
    private fun regroup(success: State.Success, members: List<ASTNode>): State.Success =
        State.Success(
            node = GroupedNode(MarkdownElementTypes.MARKDOWN_FILE, members),
            content = success.content,
            linksLookedUp = success.linksLookedUp,
            referenceLinkHandler = success.referenceLinkHandler
        )

    /**
     * A synthetic container node exposing exactly [children] under [type], reusing the real
     * (already-parsed, unmodified) nodes it groups — their own [ASTNode.parent] chain is untouched,
     * so any rendering logic that walks upward for context (nesting depth, list kind, …) still sees
     * the true original tree; this node only changes what a *caller starting here* iterates.
     */
    private class GroupedNode(
        override val type: IElementType,
        override val children: List<ASTNode>
    ) : ASTNode {
        override val startOffset: Int = children.firstOrNull()?.startOffset ?: 0
        override val endOffset: Int = children.lastOrNull()?.endOffset ?: 0
        override val parent: ASTNode? = null
    }
}

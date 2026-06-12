package com.eskerra.go.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.eskerra.go.core.markdown.DateToken
import com.eskerra.go.core.markdown.VaultReadonlyLink
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import java.time.LocalDateTime
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

/**
 * Builds a [MarkdownAnnotator] that adds the two Eskerra inline rules on top of the standard
 * markdown rendering (spec §8.3 link tones + reminder pills). Both rules degrade gracefully: if a
 * node is not intercepted, the library renders it with its defaults.
 */
object VaultMarkdownAnnotator {

    fun build(
        registry: NoteRegistry,
        status: VaultReadonlyLink.IndexStatus,
        now: LocalDateTime,
        onLinkTap: (String) -> Unit,
        sourceNoteId: NoteId? = null
    ): MarkdownAnnotator = markdownAnnotator(
        annotate = { content, node ->
            when (node.type) {
                MarkdownElementTypes.INLINE_LINK ->
                    appendLink(this, content, node, registry, status, sourceNoteId, onLinkTap)
                MarkdownTokenTypes.TEXT ->
                    appendTextWithPills(this, textOf(content, node), now)
                else -> false
            }
        }
    )

    private fun textOf(content: String, node: ASTNode): String =
        content.substring(node.startOffset, node.endOffset)

    private fun childOfType(node: ASTNode, type: org.intellij.markdown.IElementType): ASTNode? =
        node.children.firstOrNull { it.type == type }

    private fun appendLink(
        builder: AnnotatedString.Builder,
        content: String,
        node: ASTNode,
        registry: NoteRegistry,
        status: VaultReadonlyLink.IndexStatus,
        sourceNoteId: NoteId?,
        onLinkTap: (String) -> Unit
    ): Boolean {
        val labelNode = childOfType(node, MarkdownElementTypes.LINK_TEXT) ?: return false
        val destNode = childOfType(node, MarkdownElementTypes.LINK_DESTINATION) ?: return false
        val label = textOf(content, labelNode).removeSurrounding("[", "]")
        val href = textOf(content, destNode).trim().removeSurrounding("<", ">")

        val tone = VaultReadonlyLink.toneFor(href, registry, status, sourceNoteId)
        val style = TextLinkStyles(style = SpanStyle(color = VaultMarkdownTokens.linkColor(tone)))
        builder.withLink(
            LinkAnnotation.Clickable(
                tag = href,
                styles = style,
                linkInteractionListener = { onLinkTap(href) }
            )
        ) {
            append(label)
        }
        return true
    }

    private fun appendTextWithPills(
        builder: AnnotatedString.Builder,
        text: String,
        now: LocalDateTime
    ): Boolean {
        val spans = DateToken.collectTokenSpansInLine(text)
        if (spans.isEmpty()) {
            return false
        }
        var cursor = 0
        for (span in spans) {
            if (span.tokenStartInLine > cursor) {
                builder.append(text.substring(cursor, span.tokenStartInLine))
            }
            val tone = DateToken.pillTone(span.value, now)
            val colors = VaultMarkdownTokens.pillColors(tone)
            val pretty = DateToken.formatDateTokenPretty(span.value, now)
            builder.withStyle(
                SpanStyle(background = colors.background, color = colors.foreground)
            ) {
                append(" $pretty ")
            }
            cursor = span.tokenStartInLine + span.token.length
        }
        if (cursor < text.length) {
            builder.append(text.substring(cursor))
        }
        return true
    }
}

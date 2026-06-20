package com.eskerra.go.core.markdown

import com.mikepenz.markdown.model.Input
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * A vault note body pre-parsed into render-ready segments so the UI can paint it atomically — in a
 * single frame, with no empty loading slot and no per-block pop-in (spec §8, §13). Produced off the
 * main thread by [prepareVaultMarkdown]; rendered synchronously by the shared markdown view.
 */
data class PreparedMarkdown(val segments: List<PreparedSegment>)

/** One render unit of a [PreparedMarkdown]: either a standard markdown run or a callout card. */
sealed interface PreparedSegment {
    /** A run of standard markdown, already parsed into a renderer [State]. */
    data class Markdown(val state: State) : PreparedSegment

    /** A callout block with resolved metadata and its inner body pre-parsed ([body] null when empty). */
    data class Callout(
        val resolved: CalloutHeader.ResolvedCallout,
        val title: String,
        val body: State?
    ) : PreparedSegment
}

/**
 * Strips YAML frontmatter, optionally applies wiki→synthetic-link preprocessing, segments callouts,
 * and pre-parses every markdown run (callout bodies included) into a renderer [State]. This is the
 * heavy part of rendering; it is `suspend` and offloads parsing itself, so call it off the main
 * thread (e.g. behind a cache) and render the result in one shot.
 *
 * @param preprocessWikiLinks `true` for the full vault renderer; `false` for the limited inbox-detail
 *   renderer (spec §5.2), which deliberately does not resolve wiki links.
 */
suspend fun prepareVaultMarkdown(
    markdown: String,
    preprocessWikiLinks: Boolean = true
): PreparedMarkdown {
    val split = VaultMarkdownPreprocess.splitYamlFrontmatter(markdown)
    val body = if (preprocessWikiLinks) {
        VaultMarkdownPreprocess.preprocessVaultReadonlyMarkdownBody(split.body)
    } else {
        split.body
    }
    val segments = CalloutBlocks.segment(body).map { segment ->
        when (segment) {
            is CalloutBlocks.Segment.Markdown ->
                PreparedSegment.Markdown(parseMarkdown(segment.text))

            is CalloutBlocks.Segment.Callout ->
                PreparedSegment.Callout(
                    resolved = segment.resolved,
                    title = segment.title,
                    body = segment.body.takeIf { it.isNotEmpty() }?.let { parseMarkdown(it) }
                )
        }
    }
    return PreparedMarkdown(segments)
}

/** Parses a single markdown run into a renderer [State] (off the main thread; matches library defaults). */
private suspend fun parseMarkdown(content: String): State {
    val flavour = GFMFlavourDescriptor()
    return MarkdownState(
        Input(
            content = content,
            lookupLinks = true,
            flavour = flavour,
            parser = MarkdownParser(flavour),
            referenceLinkHandler = ReferenceLinkHandlerImpl()
        )
    ).parse()
}

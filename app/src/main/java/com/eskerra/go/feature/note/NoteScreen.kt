package com.eskerra.go.feature.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.model.WikiLink

/**
 * Stateless note reader. Renders fake markdown-ish [body] with clickable
 * `[[wiki links]]`. Link taps are reported through [onWikiLinkClick]; the screen
 * does not resolve or navigate by itself.
 */
@Composable
fun NoteScreen(
    title: String,
    body: String,
    onWikiLinkClick: (target: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = buildNoteText(body, onWikiLinkClick),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private val WIKI_LINK_REGEX = Regex("""\[\[([^\]]+)]]""")

/**
 * Builds an annotated string where each `[[target]]` becomes a clickable link.
 * For these fake notes the displayed text equals the link target.
 */
private fun buildNoteText(
    body: String,
    onWikiLinkClick: (target: String) -> Unit
): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in WIKI_LINK_REGEX.findAll(body)) {
        append(body.substring(lastIndex, match.range.first))

        val link = WikiLink(
            target = match.groupValues[1],
            displayText = match.groupValues[1]
        )
        withLink(
            LinkAnnotation.Clickable(
                tag = link.target,
                styles = TextLinkStyles(
                    style = SpanStyle(textDecoration = TextDecoration.Underline)
                ),
                linkInteractionListener = { onWikiLinkClick(link.target) }
            )
        ) {
            append(link.displayText)
        }

        lastIndex = match.range.last + 1
    }
    append(body.substring(lastIndex))
}

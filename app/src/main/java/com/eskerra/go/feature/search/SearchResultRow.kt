package com.eskerra.go.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eskerra.go.core.search.VaultSearchNoteResult
import com.eskerra.go.core.search.vaultSearchHighlightSegments
import com.eskerra.go.ui.theme.EskerraChromeTokens

@Composable
fun SearchResultRow(
    note: VaultSearchNoteResult,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = note.title.ifBlank { note.relativePath }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        HighlightedText(
            text = titleText,
            query = query,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            highlightColor = MaterialTheme.colorScheme.primary
        )
        HighlightedText(
            text = note.relativePath,
            query = query,
            style = MaterialTheme.typography.bodySmall,
            highlightColor = EskerraChromeTokens.Subtitle
        )
        val snippet = note.snippets.firstOrNull()
        if (snippet != null) {
            val prefix = snippet.lineNumber?.let { "$it · " }.orEmpty()
            Text(
                text = "$prefix${snippet.text}",
                style = MaterialTheme.typography.bodyMedium,
                color = EskerraChromeTokens.Subtitle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    highlightColor: androidx.compose.ui.graphics.Color
) {
    val segments = vaultSearchHighlightSegments(text, query)
    Text(
        text = buildAnnotatedString {
            for (segment in segments) {
                if (segment.highlighted) {
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
        },
        style = style
    )
}

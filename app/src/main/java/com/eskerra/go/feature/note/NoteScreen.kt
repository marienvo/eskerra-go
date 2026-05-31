package com.eskerra.go.feature.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eskerra.go.app.LocalShellChromeInsets
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderSegment

/**
 * Stateless read-only note reader. Renders precomputed [NoteReaderUiState] and reports
 * navigation through callbacks only.
 */
@Composable
fun NoteScreen(
    state: NoteReaderUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onResolvedWikiLinkClick: (NoteId) -> Unit,
    modifier: Modifier = Modifier
) {
    val chrome = LocalShellChromeInsets.current
    Column(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(
                top = chrome.top,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        when (state) {
            NoteReaderUiState.Loading -> NoteReaderLoading()
            is NoteReaderUiState.Content -> NoteReaderContent(
                title = state.title,
                path = state.path,
                canEdit = state.canEdit,
                segments = state.document.segments,
                onEdit = onEdit,
                onResolvedWikiLinkClick = onResolvedWikiLinkClick
            )
            NoteReaderUiState.NotFound -> NoteReaderMessage(
                title = "Note not found",
                body = "This note is not in the workspace.",
                onRetry = null
            )
            NoteReaderUiState.InvalidNoteId -> NoteReaderMessage(
                title = "Invalid note",
                body = "This note path is not valid.",
                onRetry = null
            )
            is NoteReaderUiState.Error -> NoteReaderMessage(
                title = "Could not open note",
                body = state.message,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun NoteReaderLoading() {
    val chrome = LocalShellChromeInsets.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoteReaderContent(
    title: String,
    path: String,
    canEdit: Boolean,
    segments: List<NoteReaderSegment>,
    onEdit: () -> Unit,
    onResolvedWikiLinkClick: (NoteId) -> Unit
) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = path,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (canEdit) {
            Button(
                onClick = onEdit,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text("Edit")
            }
        }
        Text(
            text = buildReaderText(
                segments = segments,
                onResolvedWikiLinkClick = onResolvedWikiLinkClick,
                bodyColor = MaterialTheme.colorScheme.onSurface,
                mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NoteReaderMessage(title: String, body: String, onRetry: (() -> Unit)?) {
    val chrome = LocalShellChromeInsets.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = chrome.bottom, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

private fun buildReaderText(
    segments: List<NoteReaderSegment>,
    onResolvedWikiLinkClick: (NoteId) -> Unit,
    bodyColor: Color,
    mutedColor: Color
): AnnotatedString = buildAnnotatedString {
    val bodyStyle = SpanStyle(color = bodyColor)
    val mutedStyle = SpanStyle(color = mutedColor)
    val linkStyle = SpanStyle(color = bodyColor, textDecoration = TextDecoration.Underline)

    segments.forEach { segment ->
        when (segment) {
            is NoteReaderSegment.Text -> withStyle(bodyStyle) {
                append(segment.text)
            }
            is NoteReaderSegment.ResolvedLink -> {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = segment.target.value,
                        styles = TextLinkStyles(style = linkStyle),
                        linkInteractionListener = {
                            onResolvedWikiLinkClick(segment.target)
                        }
                    )
                ) {
                    append(segment.label)
                }
            }
            is NoteReaderSegment.MissingLink -> {
                withStyle(mutedStyle) {
                    append(segment.label)
                    append(" (missing)")
                }
            }
            is NoteReaderSegment.AmbiguousLink -> {
                withStyle(mutedStyle) {
                    append(segment.label)
                    append(" (ambiguous: ${segment.candidateCount} matches)")
                }
            }
        }
    }
}

package com.eskerra.go.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/** Resting height of the compact single-line pill; its 50% radius drives [PillCorner]. */
private val SingleLineHeight = 56.dp

/** Absolute corner radius: 50% of [SingleLineHeight]. Kept fixed so the shape stays identical
 *  once the input grows into multi-line mode (a taller card with the same rounded corners). */
private val PillCorner = 28.dp

private val ControlSize = 40.dp
private val ControlSpacing = 8.dp
private val HorizontalInset = 12.dp
private val FieldMaxHeight = 180.dp

/**
 * Bottom pill that doubles as the inbox-note composer and the live vault-search input. [searchMode]
 * (owned by the shell so it survives navigation) picks which: the caller wires [value]/[onValueChange]
 * to the inbox-note draft or the shared search query, and [onSubmit] to save-note or open results.
 */
@Composable
fun ShellNewNoteInput(
    searchMode: Boolean,
    onSearchModeChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    var isMultiline by remember { mutableStateOf(false) }

    // Horizontal space the toggle + action button (and the gaps around them) claim from the field
    // in single-line mode. In multi-line mode the field is full width, so this is exactly how much
    // narrower the field becomes when collapsing back — used to keep the mode switch stable.
    val reservedPx = with(LocalDensity.current) { (ControlSize * 2 + ControlSpacing * 2).toPx() }
    val onTextLayout: (TextLayoutResult) -> Unit = { result ->
        isMultiline = when {
            result.lineCount > 1 -> true
            // Currently single-line and still one line at the narrow width: stay single-line.
            !isMultiline -> false
            // Currently multi-line and now one line at full width: collapse only if the text also
            // fits the narrower single-line row. getLineRight(0) is the intrinsic text width, so
            // this comparison is stable and does not oscillate at the wrap boundary.
            else -> result.getLineRight(0) + reservedPx > result.size.width
        }
    }

    val placeholder = if (searchMode) "Search in vault..." else "Write a new inbox note..."

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        shape = RoundedCornerShape(PillCorner),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        // The text field stays at one unconditional call site inside this persistent Row so its
        // composition identity (and keyboard focus) survives the single-line <-> multi-line switch.
        // Only the toggle and action button move: inline beside the field in single-line mode, or
        // down into a separate action row in multi-line mode.
        Column(modifier = Modifier.padding(vertical = if (isMultiline) 12.dp else 0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isMultiline) Modifier else Modifier.heightIn(min = SingleLineHeight))
                    .padding(horizontal = HorizontalInset),
                horizontalArrangement = Arrangement.spacedBy(ControlSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isMultiline) {
                    SearchModeToggle(searchMode = searchMode, onToggle = onSearchModeChange)
                }
                NewNoteField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = isSaving,
                    placeholder = placeholder,
                    onTextLayout = onTextLayout,
                    modifier = Modifier.weight(1f)
                )
                if (!isMultiline) {
                    ShellNewNoteActionButton(
                        searchMode = searchMode,
                        isSaving = isSaving,
                        enabled = submitEnabled,
                        onClick = onSubmit
                    )
                }
            }
            if (isMultiline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HorizontalInset, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchModeToggle(searchMode = searchMode, onToggle = onSearchModeChange)
                    Spacer(modifier = Modifier.weight(1f))
                    ShellNewNoteActionButton(
                        searchMode = searchMode,
                        isSaving = isSaving,
                        enabled = submitEnabled,
                        onClick = onSubmit
                    )
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = HorizontalInset + 8.dp, end = HorizontalInset, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchModeToggle(
    searchMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    IconToggleButton(
        checked = searchMode,
        onCheckedChange = onToggle,
        modifier = Modifier.size(ControlSize)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = if (searchMode) "Switch to note mode" else "Switch to search mode",
            tint = if (searchMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ShellNewNoteActionButton(
    searchMode: Boolean,
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(ControlSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        when {
            isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            searchMode -> Icon(Icons.Filled.Search, contentDescription = "Search")
            else -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Save note")
        }
    }
}

@Composable
private fun NewNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    placeholder: String,
    onTextLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(max = FieldMaxHeight)
            .verticalScroll(scrollState),
        readOnly = readOnly,
        maxLines = Int.MAX_VALUE,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        onTextLayout = onTextLayout,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = placeholderColor
                    )
                }
                innerTextField()
            }
        }
    )
}

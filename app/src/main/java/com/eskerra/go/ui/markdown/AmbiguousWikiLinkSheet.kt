package com.eskerra.go.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteRegistry

/** Modal background per §10 dark-mode chrome contract. */
private val SheetBackground = Color(0xFF1D1D1D)

/**
 * Bottom sheet picker shown when a wiki link resolves to multiple candidates (spec §9.2 rule 4).
 * Lists each candidate's title and vault-relative path; tapping one closes the sheet and invokes
 * [onPickNote].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguousWikiLinkSheet(
    candidates: List<NoteId>,
    registry: NoteRegistry,
    onPickNote: (NoteId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Multiple notes match",
                color = Color(0xFFF5F5F5),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            candidates.forEachIndexed { index, noteId ->
                if (index > 0) {
                    HorizontalDivider(
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                val summary = registry.notes.firstOrNull { it.id == noteId }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickNote(noteId) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = summary?.title?.takeIf { it.isNotBlank() }
                            ?: noteId.value.substringAfterLast('/').removeSuffix(".md"),
                        color = Color(0xFFF5F5F5),
                        fontSize = 15.sp
                    )
                    Text(
                        text = noteId.value,
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.DatedNoteHeader
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import com.eskerra.go.feature.todayhub.TodayHubUiState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private data class LoadedLinks(
    val workspace: WorkspaceConfig,
    val marker: NoteId,
    val links: List<TodayHubDatedLink>
)

@Composable
internal fun datedNoteHeader(
    ctx: AppNavGraphContext,
    noteId: NoteId,
    document: NoteReaderDocument?,
    hub: TodayHubUiState.Content?
): DatedNoteHeader? {
    val marker = if (hub != null && document != null) {
        hub.hubs.firstOrNull {
            NoteflowDatedNotes.neighbours(document.registry, it.noteId, noteId) != null
        }?.noteId
    } else {
        null
    }
    val neighbours = if (marker != null && document != null) {
        NoteflowDatedNotes.neighbours(document.registry, marker, noteId)
    } else {
        null
    }
    val otherLinks by produceState<LoadedLinks?>(
        null,
        marker,
        noteId,
        document,
        hub,
        ctx.currentConfig
    ) {
        if (marker != null && document != null && hub != null && marker != hub.activeHubId) {
            withFrameNanos { }
            val links = ctx.loadTodayHub.datedLinks(
                ctx.currentConfig,
                ctx.filesDir,
                document.registry,
                marker
            ).getOrNull()
            value = links?.let { LoadedLinks(ctx.currentConfig, marker, it) }
        }
    }
    if (marker == null || neighbours == null || hub == null) return null
    val links = if (marker == hub.activeHubId) {
        hub.datedLinks
    } else {
        otherLinks?.takeIf { it.marker == marker && it.workspace == ctx.currentConfig }?.links
            ?: return null
    }
    return DatedNoteHeader(
        links,
        noteId,
        neighbours.previous,
        neighbours.next,
        DatedNoteHeader.dateLabel(
            neighbours.date,
            Clock.System.todayIn(TimeZone.currentSystemDefault())
        )
    )
}

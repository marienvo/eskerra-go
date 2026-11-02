package com.eskerra.go.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.NoteReaderDocument
import com.eskerra.go.core.model.NoteRegistry
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.todayhub.DatedNoteHeader
import com.eskerra.go.core.todayhub.NoteflowDatedNotes
import com.eskerra.go.core.todayhub.TodayHubDatedLink
import com.eskerra.go.core.todayhub.TodayHubRef
import com.eskerra.go.feature.todayhub.TodayHubUiState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private data class LoadedLinks(
    val workspace: WorkspaceConfig,
    val marker: NoteId,
    val links: List<TodayHubDatedLink>
)

internal class DatedNoteHeaderRetention {
    var previous: DatedNoteHeader? = null
}

@Composable
internal fun datedNoteHeader(
    ctx: AppNavGraphContext,
    noteId: NoteId,
    document: NoteReaderDocument?,
    hub: TodayHubUiState.Content?,
    notificationOpen: Boolean,
    onNotificationHub: (NoteId) -> Unit
): DatedNoteHeader? {
    val marker = hub?.let {
        datedNoteMarker(it.hubs, document?.registry ?: it.registry, noteId)
    }
    LaunchedEffect(notificationOpen, noteId, marker) {
        if (notificationOpen && marker != null) onNotificationHub(marker)
    }
    val resolved = resolveDatedNoteHeader(ctx, noteId, document, hub, marker)
    val targetIsDated = marker != null
    val previous = ctx.datedHeaderRetention.previous
    val retained = datedHeaderForNavigation(
        resolved,
        previous,
        noteId,
        targetIsDated,
        retainPrevious = !notificationOpen
    )
    SideEffect {
        ctx.datedHeaderRetention.previous = retained
    }
    return retained
}

@Composable
private fun resolveDatedNoteHeader(
    ctx: AppNavGraphContext,
    noteId: NoteId,
    document: NoteReaderDocument?,
    hub: TodayHubUiState.Content?,
    marker: NoteId?
): DatedNoteHeader? {
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

internal fun datedNoteMarker(
    hubs: List<TodayHubRef>,
    registry: NoteRegistry,
    noteId: NoteId
): NoteId? = hubs.firstOrNull {
    NoteflowDatedNotes.neighbours(registry, it.noteId, noteId) != null
}?.noteId

internal fun datedHeaderForNavigation(
    resolved: DatedNoteHeader?,
    previous: DatedNoteHeader?,
    target: NoteId,
    targetIsDated: Boolean = false,
    retainPrevious: Boolean = true
): DatedNoteHeader? = if (!retainPrevious) {
    resolved
} else {
    resolved ?: previous?.takeIf {
        targetIsDated ||
            target == it.previous ||
            target == it.next ||
            it.links.any { link -> link.noteId == target }
    }
}

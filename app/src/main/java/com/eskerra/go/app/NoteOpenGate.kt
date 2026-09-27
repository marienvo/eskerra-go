package com.eskerra.go.app

import androidx.navigation.NavHostController
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.usecase.WarmNote
import com.eskerra.go.data.perf.NoteNavTrace
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a note tap waits for the target to warm before navigating regardless. */
internal const val NOTE_OPEN_WARM_BUDGET_MS = 150L

/**
 * Waits up to [NOTE_OPEN_WARM_BUDGET_MS] for [noteId]'s content and parsed body to warm — the same
 * [WarmNote] step the background prefetch scheduler uses — before navigating to it. A target that
 * is already warm (from prefetch, or a prior visit still in cache) returns immediately, so this
 * budget is only ever spent on a genuine miss; a note that is still cold once the budget elapses
 * still navigates, and the reader's own `Loading` state covers the remainder.
 *
 * Every note-opening tap in the app routes through this so the wait is consistent whether the tap
 * lands on a link inside a note, an ambiguous-link pick, an inbox tile, a Today Hub cell link, or a
 * search result.
 */
/** Coordinates pending note opens so a newer tap always wins over an older warming request. */
internal class NoteOpenGate {
    private var pendingOpen: Job? = null
    private var latestRequestId = 0L

    fun open(
        scope: CoroutineScope,
        warm: suspend () -> Unit,
        onReadyToNavigate: (warmedInBudget: Boolean) -> Unit
    ) {
        val requestId = ++latestRequestId
        pendingOpen?.cancel()
        pendingOpen = scope.launch {
            val warmedInBudget = withTimeoutOrNull(NOTE_OPEN_WARM_BUDGET_MS) { warm() } != null
            if (requestId == latestRequestId) {
                onReadyToNavigate(warmedInBudget)
            }
        }
    }
}

internal fun NoteOpenGate.openNoteWithWarmBudget(
    scope: CoroutineScope,
    warmNote: WarmNote,
    config: WorkspaceConfig,
    filesDir: File,
    navController: NavHostController,
    noteId: NoteId
) {
    NoteNavTrace.log("tap.start", "noteId=${noteId.value}")
    open(
        scope = scope,
        warm = { warmNote(config, filesDir, noteId) }
    ) { warmedInBudget ->
        NoteNavTrace.log(
            "tap.warmed",
            "noteId=${noteId.value} withinBudget=$warmedInBudget"
        )
        navController.navigate(AppRoute.note(noteId))
        NoteNavTrace.log("tap.navigate", "noteId=${noteId.value}")
    }
}

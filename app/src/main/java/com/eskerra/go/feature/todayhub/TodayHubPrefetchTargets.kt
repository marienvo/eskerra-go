package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.markdown.PrefetchLinkTargets
import com.eskerra.go.core.model.NoteId

/**
 * Pure collection of prefetch candidates from the Today Hub's currently visible content: the intro
 * markdown and the loaded week row's column bodies. Document order — there is no scroll-viewport
 * concept for the home screen, and it does not need one: home prefetch always runs at the lowest
 * priority anyway, since [com.eskerra.go.core.usecase.NotePrefetchScheduler.submit] from any open
 * note immediately supersedes it.
 */
object TodayHubPrefetchTargets {

    fun resolve(state: TodayHubUiState.Content): List<NoteId> {
        val ids = LinkedHashSet<NoteId>()

        PrefetchLinkTargets.resolve(state.introMarkdown, state.activeHubId, state.registry)
            .forEach { ids.add(it) }

        val row = state.row
        if (row != null) {
            row.columns.forEach { columnMarkdown ->
                PrefetchLinkTargets.resolve(columnMarkdown, row.rowNoteId, state.registry)
                    .forEach { ids.add(it) }
            }
        }

        return ids.toList()
    }
}

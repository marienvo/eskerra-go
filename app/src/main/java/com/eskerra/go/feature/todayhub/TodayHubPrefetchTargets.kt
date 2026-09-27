package com.eskerra.go.feature.todayhub

import com.eskerra.go.core.markdown.PreparedMarkdownLinks
import com.eskerra.go.core.model.NoteId
import com.eskerra.go.core.repository.ParsedMarkdownCachePort

/**
 * Collection of prefetch candidates from the Today Hub's currently visible content: the intro
 * markdown and the loaded week row's column bodies. Document order — there is no scroll-viewport
 * concept for the home screen, and it does not need one: home prefetch always runs at the lowest
 * priority anyway, since [com.eskerra.go.core.usecase.NotePrefetchScheduler.submit] from any open
 * note immediately supersedes it.
 *
 * Uses [PreparedMarkdownLinks] against the *parsed* body — the same AST
 * [com.eskerra.go.ui.markdown.VaultMarkdownView] renders these cells from — so a link that is
 * tappable on the home screen is always among the candidates. [parsedMarkdownCache] is the same
 * shared instance the renderer already populated, so this is a cache hit, not a re-parse.
 */
object TodayHubPrefetchTargets {

    suspend fun resolve(
        state: TodayHubUiState.Content,
        parsedMarkdownCache: ParsedMarkdownCachePort
    ): List<NoteId> {
        val ids = LinkedHashSet<NoteId>()

        // Mirrors TodayHubScreen's own `introMarkdown.isNotBlank()` guard: an empty intro is never
        // rendered, so there is nothing to prefetch links from.
        if (state.introMarkdown.isNotBlank()) {
            val introPrepared = parsedMarkdownCache.get(state.introMarkdown)
            PreparedMarkdownLinks.resolve(introPrepared, state.activeHubId, state.registry)
                .targets.forEach { ids.add(it.noteId) }
        }

        val row = state.row
        if (row != null) {
            // Mirrors HubColumn's own `body.isBlank()` guard: a blank column shows "—" instead of
            // rendering, so there is nothing to prefetch links from.
            row.columns.filter { it.isNotBlank() }.forEach { columnMarkdown ->
                val columnPrepared = parsedMarkdownCache.get(columnMarkdown)
                PreparedMarkdownLinks.resolve(columnPrepared, row.rowNoteId, state.registry)
                    .targets.forEach { ids.add(it.noteId) }
            }
        }

        return ids.toList()
    }
}

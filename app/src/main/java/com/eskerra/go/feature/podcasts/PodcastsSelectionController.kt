package com.eskerra.go.feature.podcasts

import com.eskerra.go.core.model.PodcastEpisode
import com.eskerra.go.core.usecase.MarkPodcastEpisodesPlayedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private typealias MarkEpisodesFn =
    suspend (List<PodcastEpisode>) -> Result<MarkPodcastEpisodesPlayedResult>

internal class PodcastsSelectionController(
    private val scope: CoroutineScope,
    private val readContent: () -> PodcastsUiState.Content?,
    private val updateContent: (PodcastsUiState.Content) -> Unit,
    private val onExitMiniPlayerArtworkMode: () -> Unit,
    private val markEpisodes: MarkEpisodesFn,
    private val onMarkedUpdated: suspend () -> Unit
) {
    fun onArtworkClick(episode: PodcastEpisode) {
        val current = readContent() ?: return
        if (current.markInFlight) return
        onExitMiniPlayerArtworkMode()
        val updated = current.selectedEpisodeIds.toMutableSet()
        if (episode.id in updated) {
            updated.remove(episode.id)
        } else {
            updated.add(episode.id)
        }
        updateContent(current.copy(selectedEpisodeIds = updated, markError = null))
    }

    fun clear() {
        val current = readContent() ?: return
        if (current.selectedEpisodeIds.isEmpty() && current.markError == null) return
        updateContent(current.copy(selectedEpisodeIds = emptySet(), markError = null))
    }

    fun markSelected() {
        val current = readContent() ?: return
        if (current.selectedEpisodeIds.isEmpty() || current.markInFlight) return
        val episodes = current.sections
            .flatMap { it.episodes }
            .filter { it.id in current.selectedEpisodeIds }
        if (episodes.isEmpty()) return
        scope.launch {
            updateContent(current.copy(markInFlight = true, markError = null))
            markEpisodes(episodes)
                .onSuccess { result ->
                    if (result.updated) {
                        updateContent(
                            current.copy(
                                selectedEpisodeIds = emptySet(),
                                markInFlight = false,
                                markError = null
                            )
                        )
                        onMarkedUpdated()
                    } else {
                        updateContent(
                            current.copy(markInFlight = false, selectedEpisodeIds = emptySet())
                        )
                    }
                }
                .onFailure {
                    updateContent(
                        current.copy(
                            markInFlight = false,
                            markError = PodcastsActionError.MarkSelectedFailed
                        )
                    )
                }
        }
    }
}

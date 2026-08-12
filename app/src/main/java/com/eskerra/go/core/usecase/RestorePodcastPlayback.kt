package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastPlaybackPhase
import com.eskerra.go.core.model.WorkspaceConfig
import com.eskerra.go.core.playlist.playbackSnapshot
import com.eskerra.go.core.playlist.reconcilePodcastPlaybackSources
import com.eskerra.go.core.repository.LocalSettingsStore
import com.eskerra.go.core.repository.PodcastPlayerDriver
import java.io.File

class RestorePodcastPlayback(
    private val loadPodcastCatalog: LoadPodcastCatalog,
    private val podcastPlaylistSync: PodcastPlaylistSync,
    private val localSettingsStore: LocalSettingsStore,
    private val podcastPlayerDriver: PodcastPlayerDriver
) {
    /**
     * [hydrated] means the player was primed with a resumable episode, so the mini player can
     * show it. [isActivelyPlaying] means audio is really playing right now — only that may move
     * the user to the Episodes tab on launch. A saved resume point is deliberately not enough:
     * it survives pause and app close, so treating it as "playing" opened Episodes on every cold
     * start until the episode was marked listened.
     */
    data class Result(val hydrated: Boolean, val isActivelyPlaying: Boolean)

    suspend operator fun invoke(
        config: WorkspaceConfig,
        filesDir: File,
        workspaceRoot: File?
    ): Result {
        // Wait for the MediaController to connect so currentNativeSession() reflects a session
        // that is already playing (e.g. when launched from the notification) instead of racing
        // the async connect and seeing nothing.
        podcastPlayerDriver.awaitConnection()
        val settings = localSettingsStore.load()
        val localSnapshot = settings.playbackSnapshot()
        val remoteEntry = workspaceRoot?.let { podcastPlaylistSync.read(it) }
        val catalog = loadPodcastCatalog(config, filesDir).getOrNull()
        val nativeSession = podcastPlayerDriver.currentNativeSession()
        val hydration = reconcilePodcastPlaybackSources(
            catalog = catalog,
            localSnapshot = localSnapshot,
            remoteEntry = remoteEntry,
            nativeSession = nativeSession
        ) ?: return Result(hydrated = false, isActivelyPlaying = false)

        // Only a live session that is playing this very episode counts as active playback; a
        // persisted snapshot or a remote playlist entry never does.
        val isActivelyPlaying = nativeSession != null &&
            nativeSession.isPlaying &&
            nativeSession.episodeId == hydration.episode.id

        // A live native session is the source of truth: adopt its actual play/pause state and
        // live position so returning from the notification reflects what is really playing,
        // rather than priming from a possibly-stale persisted snapshot.
        if (nativeSession != null && nativeSession.episodeId == hydration.episode.id) {
            podcastPlayerDriver.adoptNativeSession(hydration.episode, nativeSession)
            return Result(hydrated = true, isActivelyPlaying = isActivelyPlaying)
        }

        val current = podcastPlayerDriver.state.value
        if (current.isPlaying) {
            return Result(hydrated = true, isActivelyPlaying = true)
        }
        if (
            current.activeEpisode?.id == hydration.episode.id &&
            current.phase != PodcastPlaybackPhase.IDLE
        ) {
            return Result(hydrated = true, isActivelyPlaying = false)
        }
        podcastPlayerDriver.hydrate(
            episode = hydration.episode,
            positionMs = hydration.positionMs,
            durationMs = remoteEntry?.durationMs ?: localSnapshot?.durationMs
        )
        return Result(hydrated = true, isActivelyPlaying = false)
    }
}

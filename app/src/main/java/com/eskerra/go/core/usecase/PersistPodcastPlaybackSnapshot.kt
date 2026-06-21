package com.eskerra.go.core.usecase

import com.eskerra.go.core.model.PodcastPlaybackSnapshot
import com.eskerra.go.core.playlist.playbackSnapshot
import com.eskerra.go.core.playlist.withPlaybackSnapshot
import com.eskerra.go.core.repository.LocalSettingsStore

class PersistPodcastPlaybackSnapshot(
    private val store: LocalSettingsStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(snapshot: PodcastPlaybackSnapshot?) {
        val current = store.load()
        val normalized = snapshot?.copy(
            updatedAtMs =
            snapshot.updatedAtMs.takeIf { it > 0L } ?: clock()
        )
        store.save(current.withPlaybackSnapshot(normalized))
    }
}

class ClearPodcastPlaybackSnapshot(private val store: LocalSettingsStore) {
    suspend operator fun invoke() {
        val current = store.load()
        if (current.playbackSnapshot() == null) return
        store.save(current.withPlaybackSnapshot(null))
    }
}

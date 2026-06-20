package com.eskerra.go.app

import com.eskerra.go.core.usecase.ClearPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.PersistAppShellMode
import com.eskerra.go.core.usecase.PersistPodcastPlaybackSnapshot
import com.eskerra.go.core.usecase.RestorePodcastPlayback

/** Groups podcast shell persistence dependencies passed from [com.eskerra.go.MainActivity]. */
class PodcastShellStateWiring(
    val restorePodcastPlayback: RestorePodcastPlayback,
    val persistAppShellMode: PersistAppShellMode,
    val persistPodcastPlaybackSnapshot: PersistPodcastPlaybackSnapshot,
    val clearPodcastPlaybackSnapshot: ClearPodcastPlaybackSnapshot
)

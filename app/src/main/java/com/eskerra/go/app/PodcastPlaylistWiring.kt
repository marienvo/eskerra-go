package com.eskerra.go.app

import com.eskerra.go.core.repository.PlaylistSyncRepository
import com.eskerra.go.core.usecase.PodcastPlaylistSync
import com.eskerra.go.data.r2.PlaylistR2ConditionalFetch

/** Groups R2 playlist dependencies passed from [com.eskerra.go.MainActivity] into the app shell. */
class PodcastPlaylistWiring(
    val sync: PodcastPlaylistSync,
    val repository: PlaylistSyncRepository,
    val conditionalFetch: PlaylistR2ConditionalFetch
)

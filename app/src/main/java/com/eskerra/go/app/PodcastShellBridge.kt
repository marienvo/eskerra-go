package com.eskerra.go.app

/**
 * Cross-layer callbacks between the global mini player ([AppPodcastMiniPlayerHost]) and the
 * podcasts route ViewModel. Registered while the Episodes tab is composed.
 */
class PodcastShellBridge {
    var clearRowSelection: (() -> Unit)? = null
    var onExitMiniPlayerArtworkMode: (() -> Unit)? = null
    var refreshCatalog: (() -> Unit)? = null
    var pausePlayback: (() -> Unit)? = null
    var resumePlayback: (() -> Unit)? = null
    var seekBy: ((Long) -> Unit)? = null
    var seekTo: ((Long) -> Unit)? = null
    var stopPlayback: (() -> Unit)? = null
}

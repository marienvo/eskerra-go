package com.eskerra.go.data.player

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PodcastPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(PODCAST_SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(PODCAST_SEEK_INCREMENT_MS)
            .build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setMediaButtonPreferences(podcastMediaButtonPreferences())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Replaces the default [MediaSessionService.onTaskRemoved] behavior (do not call super).
     * The platform default checks [ExoPlayer.isPlaying], not [ExoPlayer.playWhenReady], and
     * would stop a session that is still buffering. Keep the service alive when playback is
     * intended ([ExoPlayer.playWhenReady] with a loaded item); otherwise stop to avoid a
     * dangling paused notification.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        val keepAlive = activePlayer != null &&
            activePlayer.playWhenReady &&
            activePlayer.mediaItemCount > 0
        if (!keepAlive) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}

package com.eskerra.go.data.player

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PodcastPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Keep playing in the background when the app task is swiped away, so the user can return
     * via the notification and resync. Only stop the service if nothing is actively playing,
     * otherwise a paused session would leave a dangling notification.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        val playing = activePlayer != null &&
            activePlayer.playWhenReady &&
            activePlayer.mediaItemCount > 0
        if (!playing) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
